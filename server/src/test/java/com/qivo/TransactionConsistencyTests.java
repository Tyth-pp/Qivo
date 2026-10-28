package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qivo.cache.RedisStore;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.BuyNowRequest;
import com.qivo.dto.CheckoutRequest;
import com.qivo.dto.ProductSaveRequest;
import com.qivo.dto.SkuDraftRequest;
import com.qivo.entity.OrderSubmission;
import com.qivo.enums.ProductSpecMode;
import com.qivo.enums.SkuStatus;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.OrderSubmissionMapper;
import com.qivo.result.ErrorCode;
import com.qivo.service.OrderService;
import com.qivo.service.OrderSubmissionService;
import com.qivo.service.SkuCatalogService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 真实 Spring 事务与 H2 SQL 并发回归，不替代 MySQL 迁移和锁语义验收。 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:transaction_consistency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000",
            "spring.datasource.hikari.maximum-pool-size=8",
            "spring.sql.init.schema-locations=classpath:test-schema.sql,classpath:transaction-test-schema.sql"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TransactionConsistencyTests {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired OrderSubmissionService submissions;
    @Autowired OrderService orders;
    @Autowired SkuCatalogService catalog;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean RedisStore redis;
    @MockitoSpyBean OrderSubmissionMapper receipts;

    @BeforeEach
    void seed() throws Exception {
        // 复用 V6 的表结构和 CHECK；只移除 H2 不支持的字符集与引擎语法。
        db.execute("DROP TABLE IF EXISTS order_submission");
        db.execute("DROP TABLE IF EXISTS stock_movement");
        boolean mysql;
        try (var connection = db.getDataSource().getConnection()) {
            mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        }
        String migration =
                new ClassPathResource("db/migration/V6__stock_ledger_and_order_submission.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
        if (!mysql)
            migration =
                    migration
                            .replaceAll(" CHARACTER SET ascii COLLATE ascii_bin", "")
                            .replaceAll(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String table :
                List.of(
                        "order_item",
                        "`order`",
                        "cart",
                        "product_sku",
                        "product",
                        "address",
                        "product_category",
                        "`user`")) db.update("DELETE FROM " + table);
        new ResourceDatabasePopulator(
                        new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)))
                .execute(Objects.requireNonNull(db.getDataSource()));
        db.update(
                "INSERT INTO `user`(id,username,password,status)"
                        + " VALUES(1,'tx-user','test-only',1),(2,'tx-other','test-only',1)");
        db.update("INSERT INTO product_category(id,name) VALUES(1,'测试分类')");
        db.update(
                "INSERT INTO"
                    + " address(id,user_id,receiver_name,receiver_phone,province,city,district,detail)"
                    + " VALUES(1,1,'测试收货人','13800000000','省','市','区','测试地址'),(2,2,'另一收货人','13900000000','省','市','区','另一地址')");
        db.update(
                "INSERT INTO product(id,name,category_id,status,sales,price,spec_definition)"
                        + " VALUES(100,'事务测试商品',1,1,0,12.50,'[]'),(101,'另一个商品',1,1,0,12.50,'[]')");
        db.update(
                "INSERT INTO"
                    + " product_sku(id,product_id,sku_code,spec_key,spec_values,price,stock,status)"
                    + " VALUES(9001,100,'TX-A','DEFAULT','[]',12.50,10,'ENABLED'),(9002,101,'TX-B','DEFAULT','[]',12.50,10,'ENABLED')");
    }

    BuyNowRequest request(int qty) {
        return new BuyNowRequest(
                1L, 9001L, qty, null, new BigDecimal("12.50").multiply(BigDecimal.valueOf(qty)));
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    long place(int qty) {
        return ((Number) submissions.buyNow(1L, key(), request(qty)).get("orderId")).longValue();
    }

    int n(String sql) {
        return db.queryForObject(sql, Integer.class);
    }

    void expired(long id) {
        // 同一时钟来源，避免依赖测试机器时区或应用墙钟。
        var time =
                db.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class)
                        .toLocalDateTime()
                        .minusMinutes(11);
        db.update("UPDATE `order` SET create_time=? WHERE id=?", time, id);
    }

    void race(Runnable a, Runnable b) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var x =
                    pool.submit(
                            () -> {
                                gate.await();
                                a.run();
                                return null;
                            });
            var y =
                    pool.submit(
                            () -> {
                                gate.await();
                                b.run();
                                return null;
                            });
            gate.countDown();
            x.get(20, TimeUnit.SECONDS);
            y.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateSubmissionCreatesOneOrderAndReplaysAfterCatalogChanges()
            throws Exception {
        String key = key();
        var results = new CopyOnWriteArrayList<Map<String, Object>>();
        race(
                () -> results.add(submissions.buyNow(1L, key, request(2))),
                () -> results.add(submissions.buyNow(1L, key, request(2))));
        assertEquals(results.get(0), results.get(1));
        assertEquals(1, n("SELECT COUNT(*) FROM `order`"));
        assertEquals(1, n("SELECT COUNT(*) FROM order_submission"));
        assertEquals(1, n("SELECT COUNT(*) FROM stock_movement"));
        assertEquals(8, n("SELECT stock FROM product_sku WHERE id=9001"));
        db.update("UPDATE product_sku SET price=99,status='DISABLED' WHERE id=9001");
        assertEquals(results.get(0), submissions.buyNow(1L, key, request(2)));
        assertThrows(BusinessException.class, () -> submissions.buyNow(1L, key, request(1)));
        assertThrows(BusinessException.class, () -> submissions.buyNow(1L, "short", request(1)));
    }

    @Test
    void cartRetryReturnsReceiptEvenAfterCartRowsWereConsumed() {
        db.update(
                "INSERT INTO cart(id,user_id,product_id,sku_id,quantity,checked)"
                        + " VALUES(77,1,100,9001,1,1)");
        var request = new CheckoutRequest(1L, List.of(77L), null, new BigDecimal("12.50"));
        String key = key();
        var first = submissions.checkout(1L, key, request);
        assertEquals(0, n("SELECT COUNT(*) FROM cart"));
        assertEquals(first, submissions.checkout(1L, key, request));
        assertEquals(1, n("SELECT COUNT(*) FROM `order`"));
    }

    @Test
    void sameKeyIsScopedToUserAndRequestMode() {
        String key = key();
        submissions.buyNow(1L, key, request(1));
        submissions.buyNow(2L, key, new BuyNowRequest(2L, 9001L, 1, null, new BigDecimal("12.50")));
        assertEquals(2, n("SELECT COUNT(*) FROM `order`"));
        assertThrows(
                BusinessException.class,
                () ->
                        submissions.checkout(
                                1L,
                                key,
                                new CheckoutRequest(
                                        1L, List.of(77L), null, new BigDecimal("12.50"))));
        long id = db.queryForObject("SELECT id FROM `order` WHERE user_id=1", Long.class);
        assertThrows(BusinessException.class, () -> orders.pay(id, 2L));
        assertThrows(BusinessException.class, () -> orders.cancel(id, 2L));
    }

    @Test
    void receiptFailureRollsBackOrderStockLedgerAndCartAndSameKeyCanRetry() {
        db.update(
                "INSERT INTO cart(id,user_id,product_id,sku_id,quantity,checked)"
                        + " VALUES(77,1,100,9001,1,1)");
        String key = key();
        var request = new CheckoutRequest(1L, List.of(77L), null, new BigDecimal("12.50"));
        doThrow(new IllegalStateException("回执持久化故障注入"))
                .when(receipts)
                .insert(any(OrderSubmission.class));
        try {
            assertThrows(IllegalStateException.class, () -> submissions.checkout(1L, key, request));
        } finally {
            reset(receipts);
        }
        assertEquals(0, n("SELECT COUNT(*) FROM `order`"));
        assertEquals(0, n("SELECT COUNT(*) FROM stock_movement"));
        assertEquals(0, n("SELECT COUNT(*) FROM order_submission"));
        assertEquals(10, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(1, n("SELECT COUNT(*) FROM cart"));
        submissions.checkout(1L, key, request);
        assertEquals(1, n("SELECT COUNT(*) FROM order_submission"));
    }

    @Test
    void secondSkuFailureRollsBackFirstReservation() {
        db.update("UPDATE product_sku SET stock=0 WHERE id=9002");
        db.update(
                "INSERT INTO cart(id,user_id,product_id,sku_id,quantity,checked)"
                        + " VALUES(77,1,100,9001,1,1),(78,1,101,9002,1,1)");
        assertThrows(
                BusinessException.class,
                () ->
                        submissions.checkout(
                                1L,
                                key(),
                                new CheckoutRequest(
                                        1L, List.of(77L, 78L), null, new BigDecimal("25.00"))));
        assertEquals(10, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(0, n("SELECT COUNT(*) FROM stock_movement"));
        assertEquals(0, n("SELECT COUNT(*) FROM order_submission"));
        assertEquals(2, n("SELECT COUNT(*) FROM cart"));
    }

    @Test
    void initialAndManualLedgerAreBalancedAndInvalidAdjustmentRollsBack() {
        catalog.save(
                new ProductSaveRequest(
                        null,
                        "新商品",
                        "",
                        1L,
                        "",
                        ProductSpecMode.SINGLE,
                        List.of(),
                        List.of(
                                new SkuDraftRequest(
                                        List.of(),
                                        new BigDecimal("2.00"),
                                        null,
                                        3,
                                        SkuStatus.ENABLED,
                                        ""))),
                true);
        assertEquals(
                1,
                n(
                        "SELECT COUNT(*) FROM stock_movement WHERE kind='INITIAL' AND delta=3 AND"
                                + " stock_before=0 AND stock_after=3"));
        catalog.adjustStock(100L, 9001L, -2, 9L);
        assertEquals(
                1,
                n(
                        "SELECT COUNT(*) FROM stock_movement WHERE kind='MANUAL_ADJUST' AND"
                            + " delta=-2 AND stock_before=10 AND stock_after=8 AND actor_id=9 AND"
                            + " actor_domain='ADMIN'"));
        assertThrows(BusinessException.class, () -> catalog.adjustStock(100L, 9001L, -9, 9L));
        assertEquals(8, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(2, n("SELECT COUNT(*) FROM stock_movement"));
        catalog.adjustStock(100L, 9001L, 1);
        assertEquals(9, n("SELECT stock FROM product_sku WHERE id=9001"));
    }

    @Test
    void paymentAndCancellationAreNaturallyIdempotentAndCannotReverseEachOther() {
        long paid = place(2);
        orders.pay(paid, 1L);
        orders.pay(paid, 1L);
        assertEquals(2, n("SELECT sales FROM product WHERE id=100"));
        assertThrows(BusinessException.class, () -> orders.cancel(paid, 1L));
        long canceled = place(1);
        orders.cancel(canceled, 1L);
        orders.cancel(canceled, 1L);
        assertThrows(BusinessException.class, () -> orders.pay(canceled, 1L));
        assertEquals(8, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(1, n("SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RELEASE'"));
    }

    @Test
    void simultaneousPaymentAndCancelHaveOneWinnerAndConsistentStockAndSales() throws Exception {
        for (int i = 0; i < 10; i++) {
            long id = place(1);
            int beforeStock = n("SELECT stock FROM product_sku WHERE id=9001"),
                    beforeSales = n("SELECT sales FROM product WHERE id=100");
            race(
                    () -> {
                        try {
                            orders.pay(id, 1L);
                        } catch (BusinessException expected) {
                        }
                    },
                    () -> {
                        try {
                            orders.cancel(id, 1L);
                        } catch (BusinessException expected) {
                        }
                    });
            int state = n("SELECT pay_status FROM `order` WHERE id=" + id);
            assertTrue(state == 1 || state == 3);
            assertEquals(
                    beforeStock + (state == 3 ? 1 : 0),
                    n("SELECT stock FROM product_sku WHERE id=9001"));
            assertEquals(
                    beforeSales + (state == 1 ? 1 : 0),
                    n("SELECT sales FROM product WHERE id=100"));
            assertEquals(
                    state == 3 ? 1 : 0,
                    n(
                            "SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RELEASE' AND"
                                    + " order_id="
                                    + id));
        }
    }

    @Test
    void expiredPaymentAndTwoScannersReleaseOnlyOnce() throws Exception {
        long id = place(2);
        expired(id);
        assertThrows(BusinessException.class, () -> orders.pay(id, 1L));
        race(() -> orders.cancelExpiredUnpaidOrders(), () -> orders.cancelExpiredUnpaidOrders());
        assertEquals(3, n("SELECT pay_status FROM `order` WHERE id=" + id));
        assertEquals(10, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(1, n("SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RELEASE'"));
    }

    @Test
    void paymentRechecksDeadlineAfterWaitingForOrderLock() throws Exception {
        long id = place(1);
        var pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        try {
            var future = new AtomicReference<Future<?>>();
            new TransactionTemplate(manager)
                    .executeWithoutResult(
                            status -> {
                                db.queryForList("SELECT * FROM `order` WHERE id=? FOR UPDATE", id);
                                expired(id);
                                future.set(
                                        pool.submit(
                                                () -> {
                                                    started.countDown();
                                                    assertThrows(
                                                            BusinessException.class,
                                                            () -> orders.pay(id, 1L));
                                                }));
                                try {
                                    assertTrue(started.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            });
            future.get().get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, n("SELECT pay_status FROM `order` WHERE id=" + id));
        assertEquals(0, n("SELECT sales FROM product WHERE id=100"));
    }

    @Test
    void oneFailedTimeoutDoesNotRollbackAnotherOrdersClosure() {
        long broken = place(1);
        expired(broken);
        long good =
                ((Number)
                                submissions
                                        .buyNow(
                                                1L,
                                                key(),
                                                new BuyNowRequest(
                                                        1L,
                                                        9002L,
                                                        1,
                                                        null,
                                                        new BigDecimal("12.50")))
                                        .get("orderId"))
                        .longValue();
        expired(good);
        db.update("UPDATE product_sku SET stock=2147483647 WHERE id=9001");
        assertEquals(1, orders.cancelExpiredUnpaidOrders());
        assertEquals(0, n("SELECT pay_status FROM `order` WHERE id=" + broken));
        assertEquals(3, n("SELECT pay_status FROM `order` WHERE id=" + good));
        assertEquals(
                0,
                n(
                        "SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RELEASE' AND"
                                + " order_id="
                                + broken));
        assertEquals(10, n("SELECT stock FROM product_sku WHERE id=9002"));
    }

    @Test
    void httpSubmissionRequiresKeyAndReplaysSuccessfulOrder() throws Exception {
        String payload =
                "{\"addressId\":1,\"skuId\":9001,\"quantity\":1,\"expectedTotal\":\"12.50\"}";
        http.perform(
                        post("/api/fore/order/buy-now")
                                .with(user("buyer").roles("USER"))
                                .requestAttr(SecurityConstant.REQUEST_USER_ID, 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isBadRequest());
        String key = key();
        for (int i = 0; i < 2; i++)
            http.perform(
                            post("/api/fore/order/buy-now")
                                    .with(user("buyer").roles("USER"))
                                    .requestAttr(SecurityConstant.REQUEST_USER_ID, 1L)
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                    .andExpect(status().isOk());
        assertEquals(1, n("SELECT COUNT(*) FROM `order`"));
        assertEquals(1, n("SELECT COUNT(*) FROM order_submission"));
    }

    @Test
    void independentUsersCompeteForTenUnitsWithoutOverselling() throws Exception {
        for (int i = 3; i <= 22; i++) {
            db.update(
                    "INSERT INTO `user`(id,username,password,status) VALUES(?,?,'test-only',1)",
                    i,
                    "concurrent-" + i);
            db.update(
                    "INSERT INTO"
                        + " address(id,user_id,receiver_name,receiver_phone,province,city,district,detail)"
                        + " VALUES(?,?,'测试','13800000000','省','市','区','地址')",
                    i,
                    i);
        }
        var pool = Executors.newFixedThreadPool(10);
        var gate = new CountDownLatch(1);
        var success = new AtomicInteger();
        var rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long user = 3; user <= 22; user++) {
                long owner = user;
                futures.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    try {
                                        submissions.buyNow(
                                                owner,
                                                key(),
                                                new BuyNowRequest(
                                                        owner,
                                                        9001L,
                                                        1,
                                                        null,
                                                        new BigDecimal("12.50")));
                                        success.incrementAndGet();
                                    } catch (BusinessException expected) {
                                        assertEquals(
                                                ErrorCode.PRODUCT_STOCK_INSUFFICIENT.getCode(),
                                                expected.getCode());
                                        rejected.incrementAndGet();
                                    }
                                    return null;
                                }));
            }
            gate.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, success.get());
        assertEquals(10, rejected.get());
        assertEquals(0, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(10, n("SELECT COUNT(*) FROM `order`"));
        assertEquals(10, n("SELECT COUNT(*) FROM order_submission"));
        assertEquals(10, n("SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RESERVE'"));
        assertEquals(-10, n("SELECT SUM(delta) FROM stock_movement"));
    }

    @Test
    void reachedDeadlinePaymentAndTimeoutCompetitionCannotPayOrReleaseTwice() throws Exception {
        long id = place(1);
        var deadline =
                db.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class)
                        .toLocalDateTime()
                        .minusMinutes(10);
        db.update("UPDATE `order` SET create_time=? WHERE id=?", deadline, id);
        race(
                () -> {
                    assertThrows(BusinessException.class, () -> orders.pay(id, 1L));
                },
                () -> orders.cancelExpiredUnpaidOrders());
        assertEquals(3, n("SELECT pay_status FROM `order` WHERE id=" + id));
        assertEquals(10, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(0, n("SELECT sales FROM product WHERE id=100"));
        assertEquals(
                1,
                n(
                        "SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RELEASE' AND"
                                + " order_id="
                                + id));
    }
    @Test
    void twoBuyersCompeteForTheLastUnitAndOnlyOneReservationCommits() throws Exception {
        db.update("UPDATE product_sku SET stock=1 WHERE id=9001");
        var success = new AtomicInteger();
        var insufficient = new AtomicInteger();
        race(
                () -> buyLastUnit(1L, success, insufficient),
                () -> buyLastUnit(2L, success, insufficient));
        assertEquals(1, success.get());
        assertEquals(1, insufficient.get());
        assertEquals(0, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(1, n("SELECT COUNT(*) FROM `order`"));
        assertEquals(1, n("SELECT COUNT(*) FROM order_submission"));
        assertEquals(1, n("SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RESERVE'"));
        assertEquals(-1, n("SELECT SUM(delta) FROM stock_movement"));
        assertEquals(0, n("SELECT sales FROM product WHERE id=100"));
    }

    private void buyLastUnit(Long owner, AtomicInteger success, AtomicInteger insufficient) {
        try {
            submissions.buyNow(owner, key(), new BuyNowRequest(
                    owner, 9001L, 1, null, new BigDecimal("12.50")));
            success.incrementAndGet();
        } catch (BusinessException failure) {
            assertEquals(ErrorCode.PRODUCT_STOCK_INSUFFICIENT.getCode(), failure.getCode());
            insufficient.incrementAndGet();
        }
    }

    @Test
    void concurrentPaymentsDoNotDeductReservedStockOrIncrementSalesTwice() throws Exception {
        long order = place(2);
        race(() -> orders.pay(order, 1L), () -> orders.pay(order, 1L));
        assertEquals(1, n("SELECT pay_status FROM `order` WHERE id=" + order));
        assertEquals(8, n("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(2, n("SELECT sales FROM product WHERE id=100"));
        assertEquals(1, n("SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RESERVE'"));
        assertEquals(-2, n("SELECT SUM(delta) FROM stock_movement"));
        assertEquals(1, n("SELECT COUNT(*) FROM order_submission"));
    }

}
