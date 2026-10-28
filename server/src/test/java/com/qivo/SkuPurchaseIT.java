package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.cache.RedisStore;
import com.qivo.development.DevelopmentDataInitializer;
import com.qivo.dto.*;
import com.qivo.entity.*;
import com.qivo.enums.*;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.*;
import com.qivo.migration.SkuMigrationPreflight;
import com.qivo.service.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/** 显式真实 MySQL 购买链路验收；只在随机库写数据，Redis 在本用例中使用替身。 */
class SkuPurchaseIT {
    @Test
    void latestEmptySchemaCanBeExplicitlySeededAndRejectsASecondInitialization() throws Exception {
        assertTrue(Boolean.getBoolean("qivo.sku.purchase"));
        var settings = SkuMigrationIT.settings();
        assertEquals(settings.get("database"), System.getProperty("qivo.sku.source"));
        String database =
                "qivo_sku_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        + "_seed";
        String base = "jdbc:mysql://" + settings.get("host") + ":" + settings.get("port") + "/";
        String options = "?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user = settings.get("username").toString(),
                password = settings.get("password").toString();
        try (var admin =
                DriverManager.getConnection(
                        base + settings.get("database") + options, user, password)) {
            try (var sql = admin.createStatement()) {
                sql.execute(
                        "CREATE DATABASE `"
                                + database
                                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            try {
                Flyway.configure()
                        .dataSource(base + database + options, user, password)
                        .load()
                        .migrate();
                try (var c =
                        DriverManager.getConnection(base + database + options, user, password)) {
                    var passwords =
                            Map.of(
                                    "admin",
                                    "seedAdmin123",
                                    "product_admin",
                                    "seedOperator123",
                                    "buyer",
                                    "seedBuyer123",
                                    "buyer_other",
                                    "seedOther123");
                    DevelopmentDataInitializer.initialize(c, database, passwords);
                    try (var sql = c.createStatement();
                            var rows = sql.executeQuery("SELECT COUNT(*) FROM product_sku")) {
                        rows.next();
                        assertEquals(5, rows.getInt(1));
                    }
                    try (var sql = c.createStatement();
                            var rows =
                                    sql.executeQuery(
                                            "SELECT COUNT(*) FROM cart WHERE sku_id IS NOT NULL")) {
                        rows.next();
                        assertEquals(2, rows.getInt(1));
                    }
                    try (var sql = c.createStatement();
                            var rows =
                                    sql.executeQuery(
                                            "SELECT COUNT(*) FROM order_item WHERE"
                                                    + " sku_snapshot_source='CURRENT'")) {
                        rows.next();
                        assertEquals(2, rows.getInt(1));
                    }
                    assertThrows(
                            IllegalStateException.class,
                            () -> DevelopmentDataInitializer.initialize(c, database, passwords));
                }
            } finally {
                assertTrue(database.matches("qivo_sku_[a-f0-9]{12}_seed"));
                try (var sql = admin.createStatement()) {
                    sql.execute("DROP DATABASE `" + database + "`");
                }
            }
        }
    }

    @TestConfiguration
    static class IsolatedCache {
        @Bean
        @Primary
        RedisStore isolatedRedis() {
            return mock(RedisStore.class);
        }
    }

    @Test
    void skuCatalogCartCheckoutSnapshotsAndConcurrentStockWorkInIsolatedMysql() throws Exception {
        assertTrue(Boolean.getBoolean("qivo.sku.purchase"), "必须显式开启购买链路验收");
        var settings = SkuMigrationIT.settings();
        assertEquals(settings.get("database"), System.getProperty("qivo.sku.source"));
        String user = settings.get("username").toString(),
                password = settings.get("password").toString();
        String base = "jdbc:mysql://" + settings.get("host") + ":" + settings.get("port") + "/";
        String opts =
                "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String database =
                "qivo_sku_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        + "_purchase";
        try (var source =
                        DriverManager.getConnection(
                                base + settings.get("database") + opts, user, password);
                var admin =
                        DriverManager.getConnection(
                                base + settings.get("database") + opts, user, password)) {
            source.setReadOnly(true);
            var columns = SkuMigrationIT.columns(source);
            var before = SkuMigrationIT.fingerprints(source, columns);
            try (var sql = admin.createStatement()) {
                sql.execute(
                        "CREATE DATABASE `"
                                + database
                                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            String url = base + database + opts;
            try {
                Flyway.configure().dataSource(url, user, password).target("3").load().migrate();
                try (var c = DriverManager.getConnection(url, user, password)) {
                    DevelopmentDataInitializer.initialize(
                            c,
                            database,
                            Map.of(
                                    "admin",
                                    "skuAdmin123",
                                    "product_admin",
                                    "skuOperator123",
                                    "buyer",
                                    "skuBuyer123",
                                    "buyer_other",
                                    "skuOther123"));
                    SkuMigrationPreflight.validate(c);
                }
                Flyway.configure().dataSource(url, user, password).load().migrate();
                try (var context =
                        new SpringApplicationBuilder(QivoApplication.class, IsolatedCache.class)
                                .run(
                                        "--spring.profiles.active=sku-it",
                                        "--server.port=0",
                                        "--spring.datasource.url=" + url,
                                        "--spring.datasource.username=" + user,
                                        "--spring.datasource.password=" + password,
                                        "--spring.datasource.hikari.minimum-idle=0",
                                        "--spring.datasource.hikari.maximum-pool-size=20",
                                        "--spring.flyway.enabled=false",
                                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                                        "--springdoc.api-docs.enabled=false",
                                        "--springdoc.swagger-ui.enabled=false",
                                        "--jwt.secret=sku-it-only-key-at-least-thirty-two-characters",
                                        "--qivo.upload.path=./target/sku-it-uploads/",
                                        "--spring.data.redis.host=127.0.0.1",
                                        "--spring.data.redis.port=1",
                                        "--spring.data.redis.password=",
                                        "--qivo.order-timeout.enabled=false")) {
                    verify(
                            context.getBean(SkuCatalogService.class),
                            context.getBean(ProductService.class),
                            context.getBean(CartService.class),
                            context.getBean(OrderService.class),
                            context.getBean(ProductSkuMapper.class),
                            context.getBean(ProductMapper.class),
                            context.getBean(DataSource.class));
                }
                assertEquals(before, SkuMigrationIT.fingerprints(source, columns), "运行库不能因验收发生修改");
            } finally {
                assertTrue(database.matches("qivo_sku_[a-f0-9]{12}_purchase"));
                assertNotEquals(settings.get("database"), database);
                try (var sql = admin.createStatement()) {
                    sql.execute("DROP DATABASE `" + database + "`");
                }
            }
        }
    }

    private void verify(
            SkuCatalogService catalog,
            ProductService products,
            CartService carts,
            OrderService orders,
            ProductSkuMapper skus,
            ProductMapper mapper,
            DataSource ds)
            throws Exception {
        var defs =
                List.of(
                        new SkuSpecificationDefinition(
                                "color",
                                "颜色",
                                List.of(
                                        new SkuSpecificationValue("black", "黑色"),
                                        new SkuSpecificationValue("white", "白色"))));
        var black =
                new SkuDraftRequest(
                        List.of(new SkuSelection("color", "black")),
                        new BigDecimal("12.50"),
                        null,
                        10,
                        SkuStatus.ENABLED,
                        null);
        var white =
                new SkuDraftRequest(
                        List.of(new SkuSelection("color", "white")),
                        new BigDecimal("20.00"),
                        null,
                        0,
                        SkuStatus.ENABLED,
                        null);
        catalog.save(
                new ProductSaveRequest(
                        null,
                        "购买链路验收",
                        "测试商品",
                        3L,
                        "",
                        ProductSpecMode.MULTI,
                        defs,
                        List.of(black, white)),
                true);
        var p = products.page(1, 10, "购买链路验收", null, null, null).getRecords().get(0);
        products.updateStatus(p.getId(), 1);
        p = products.getById(p.getId());
        var a = p.getSkus().get(0);
        var b = p.getSkus().get(1);
        Long productId = p.getId();
        assertEquals(new BigDecimal("12.50"), p.getPrice());
        assertEquals(new BigDecimal("20.00"), p.getMaxPrice());
        assertNull(p.getDefaultSkuId());
        assertEquals(10L, p.getTotalStock());
        assertThrows(BusinessException.class, () -> carts.add(1L, b.getId(), 1));
        catalog.adjustStock(productId, b.getId(), 5);
        carts.add(1L, a.getId(), 1);
        carts.add(1L, a.getId(), 1);
        carts.add(1L, b.getId(), 1);
        var selected =
                carts.list(1L).stream().filter(c -> productId.equals(c.getProductId())).toList();
        assertEquals(2, selected.size());
        assertEquals(
                2,
                selected.stream()
                        .filter(c -> c.getSkuId().equals(a.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getQuantity());
        assertThrows(
                BusinessException.class,
                () -> orders.create(2L, 2L, null, List.of(selected.get(0).getId()), null));
        assertThrows(
                BusinessException.class,
                () -> orders.createBuyNow(1L, 2L, a.getId(), 1, null, null));
        assertThrows(
                BusinessException.class,
                () ->
                        orders.create(
                                1L,
                                1L,
                                null,
                                selected.stream().map(Cart::getId).toList(),
                                new BigDecimal("1.00")));
        assertEquals(10, skus.selectById(a.getId()).getStock());
        var result =
                orders.create(
                        1L,
                        1L,
                        "SKU 结算",
                        selected.stream().map(Cart::getId).toList(),
                        new BigDecimal("45.00"));
        Long orderId = (Long) result.get("orderId");
        assertEquals(new BigDecimal("45.00"), result.get("totalAmount"));
        assertEquals(8, skus.selectById(a.getId()).getStock());
        assertEquals(4, skus.selectById(b.getId()).getStock());
        assertTrue(carts.list(1L).stream().noneMatch(c -> productId.equals(c.getProductId())));
        assertTrue(
                carts.list(1L).stream().anyMatch(c -> c.getProductId().equals(1L)), "未提交的原购物车项保留");
        var items = orders.getItems(orderId);
        assertEquals(2, items.size());
        assertTrue(items.get(0).getSkuSpecSnapshot().contains("黑色"));
        var order = orders.getById(orderId);
        assertEquals("CURRENT", order.getAddressSnapshotSource());
        String address = order.getReceiverAddressSnapshot(),
                snapshot = items.get(0).getSkuSpecSnapshot();
        try (var c = ds.getConnection();
                var sql = c.createStatement()) {
            sql.executeUpdate("UPDATE address SET detail='已修改地址' WHERE id=1");
        }
        catalog.updateSku(
                productId,
                a.getId(),
                new SkuUpdateRequest(new BigDecimal("99.00"), null, SkuStatus.DISABLED, null));
        assertEquals(snapshot, orders.getItems(orderId).get(0).getSkuSpecSnapshot());
        assertEquals(address, orders.getById(orderId).getReceiverAddressSnapshot());
        orders.cancel(orderId, 1L);
        assertEquals(10, skus.selectById(a.getId()).getStock());
        assertEquals(5, skus.selectById(b.getId()).getStock());
        assertDoesNotThrow(() -> orders.cancel(orderId, 1L));
        assertEquals(10, skus.selectById(a.getId()).getStock());
        catalog.updateSku(
                productId,
                a.getId(),
                new SkuUpdateRequest(new BigDecimal("12.50"), null, SkuStatus.ENABLED, null));
        // Exact selected IDs remain fixed even if a different cart entry becomes checked later.
        carts.add(1L, a.getId(), 1);
        carts.add(1L, b.getId(), 1);
        var rows = carts.list(1L).stream().filter(c -> productId.equals(c.getProductId())).toList();
        catalog.adjustStock(productId, b.getId(), -5);
        int original = skus.selectById(a.getId()).getStock();
        long ordersBefore = count(ds, "SELECT COUNT(*) FROM `order`");
        assertThrows(
                BusinessException.class,
                () -> orders.create(1L, 1L, null, rows.stream().map(Cart::getId).toList(), null));
        assertEquals(original, skus.selectById(a.getId()).getStock());
        assertEquals(ordersBefore, count(ds, "SELECT COUNT(*) FROM `order`"));
        assertEquals(
                2, carts.list(1L).stream().filter(c -> productId.equals(c.getProductId())).count());
        Long singleCart =
                rows.stream()
                        .filter(c -> c.getSkuId().equals(a.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getId();
        var single = orders.create(1L, 1L, null, List.of(singleCart), null);
        assertEquals(9, skus.selectById(a.getId()).getStock());
        assertTrue(carts.list(1L).stream().anyMatch(c -> c.getSkuId().equals(b.getId())));
        orders.pay((Long) single.get("orderId"), 1L);
        assertEquals(1, mapper.selectById(productId).getSales());
        assertDoesNotThrow(() -> orders.pay((Long) single.get("orderId"), 1L));
        assertThrows(
                BusinessException.class, () -> orders.cancel((Long) single.get("orderId"), 1L));
        // Twenty real transactions, ten units: exactly ten purchases, then return only those ten.
        catalog.adjustStock(productId, a.getId(), 1);
        var successes = new ConcurrentLinkedQueue<Long>();
        var failures = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(10);
        try {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 20; i++)
                tasks.add(
                        pool.submit(
                                () -> {
                                    try {
                                        successes.add(
                                                (Long)
                                                        orders.createBuyNow(
                                                                        1L, 1L, a.getId(), 1, null,
                                                                        null)
                                                                .get("orderId"));
                                    } catch (BusinessException expected) {
                                        failures.incrementAndGet();
                                    }
                                }));
            for (var task : tasks) task.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(10, successes.size());
        assertEquals(10, failures.get());
        assertEquals(0, skus.selectById(a.getId()).getStock());
        for (var id : successes) orders.cancel(id, 1L);
        assertEquals(10, skus.selectById(a.getId()).getStock());
        var timed = orders.createBuyNow(1L, 1L, a.getId(), 1, null, null);
        Long timedId = (Long) timed.get("orderId");
        try (var c = ds.getConnection();
                var sql = c.createStatement()) {
            sql.executeUpdate(
                    "UPDATE `order` SET create_time=DATE_SUB(NOW(),INTERVAL 11 MINUTE) WHERE id="
                            + timedId);
        }
        assertThrows(BusinessException.class, () -> orders.pay(timedId, 1L));
        assertEquals(1, orders.cancelExpiredUnpaidOrders());
        assertEquals(0, orders.cancelExpiredUnpaidOrders());
        assertEquals(10, skus.selectById(a.getId()).getStock());
        assertThrows(
                BusinessException.class,
                () ->
                        catalog.save(
                                new ProductSaveRequest(
                                        productId,
                                        "改模式",
                                        "",
                                        3L,
                                        "",
                                        ProductSpecMode.SINGLE,
                                        List.of(),
                                        List.of()),
                                false));
    }

    private long count(DataSource ds, String query) throws Exception {
        try (var c = ds.getConnection();
                var sql = c.createStatement();
                var rows = sql.executeQuery(query)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
