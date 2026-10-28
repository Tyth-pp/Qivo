package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.cache.RedisStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.dto.AdminAccountRequest;
import com.qivo.dto.AdminAccountUpdateRequest;
import com.qivo.enums.AfterSaleStatus;
import com.qivo.exception.BusinessException;
import com.qivo.security.SessionRegistry;
import com.qivo.service.AdminService;
import com.qivo.service.AfterSaleService;
import com.qivo.service.OrderService;
import com.qivo.service.RefundGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:operations;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;LOCK_TIMEOUT=10000",
    "spring.sql.init.schema-locations=classpath:test-schema.sql,classpath:transaction-test-schema.sql,classpath:operations-test-schema.sql"})
@ActiveProfiles("test")
class OperationsWorkflowTests {
    @Autowired JdbcTemplate db;
    @Autowired AfterSaleService sales;
    @Autowired OrderService orders;
    @Autowired AdminService admins;
    @Autowired ObjectMapper json;
    @MockitoBean RedisStore redis;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean RefundGateway gateway;

    @BeforeEach
    void seed() throws Exception {
        for(String table:List.of("refund_attempt","after_sale","order_item","`order`","product_sku","product","product_category","address","`user`","admin_role","admin","role"))db.update("DELETE FROM "+table);
        db.execute("DROP TABLE IF EXISTS stock_movement");db.execute("DROP TABLE IF EXISTS order_submission");
        var migration=new ClassPathResource("db/migration/V6__stock_ledger_and_order_submission.sql").getContentAsString(StandardCharsets.UTF_8)
                .replace(" CHARACTER SET ascii COLLATE ascii_bin","").replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4","");
        new ResourceDatabasePopulator(new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8))).execute(Objects.requireNonNull(db.getDataSource()));
        db.update("INSERT INTO product_category(id,name) VALUES(1,'隔离测试分类')");
        db.update("INSERT INTO `user`(id,username,password,status) VALUES(1,'isolated-user','test-only',1)");
        db.update("INSERT INTO address(id,user_id,receiver_name,receiver_phone,province,city,district,detail) VALUES(1,1,'隔离买家','13800000000','省','市','区','测试地址')");
        db.update("INSERT INTO product(id,name,price,status,sales,category_id) VALUES(100,'下单商品',13,1,3,1)");
        db.update("INSERT INTO product_sku(id,product_id,sku_code,price,stock,status,spec_key,spec_values) VALUES(9001,100,'TEST-SKU',13,7,'ENABLED','DEFAULT','[]')");
        db.update("INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,create_time,address_id) VALUES(701,'REFUND-701',1,35,1,0,NOW(),1)");
        db.update("INSERT INTO order_item(id,order_id,product_id,sku_id,sku_code,product_name,quantity,unit_price,total_price,sku_snapshot_source) VALUES(801,701,100,9001,'TEST-SKU','快照商品',3,13,39,'CURRENT')");
        db.update("INSERT INTO role(id,name,code) VALUES(1,'超级管理员','super_admin'),(2,'普通角色','reader')");
        db.update("INSERT INTO admin(id,username,password,status) VALUES(1,'root','original-root',1),(2,'operator','original-other',1)");
        db.update("INSERT INTO admin_role(admin_id,role_id) VALUES(1,1),(2,2)");
        when(gateway.refund(anyString(),any())).thenReturn(new RefundGateway.Outcome(true,"模拟成功"));
    }
    int count(String sql){return db.queryForObject(sql,Integer.class);}
    void assertOriginal(){assertEquals(7,count("SELECT stock FROM product_sku WHERE id=9001"));assertEquals(3,count("SELECT sales FROM product WHERE id=100"));assertEquals(1,count("SELECT pay_status FROM `order` WHERE id=701"));}

    @Test void rejectsForeignUnpaidShippedAndDuplicateContent() {
        assertThrows(BusinessException.class,()->sales.apply(2L,701L,"request-001","不需要"));
        db.update("UPDATE `order` SET pay_status=0 WHERE id=701");assertThrows(BusinessException.class,()->sales.apply(1L,701L,"request-001","不需要"));
        db.update("UPDATE `order` SET pay_status=1,ship_status=1 WHERE id=701");assertThrows(BusinessException.class,()->sales.apply(1L,701L,"request-001","不需要"));
        db.update("UPDATE `order` SET ship_status=0 WHERE id=701");var row=sales.apply(1L,701L,"request-001","不需要");
        assertEquals(row.getId(),sales.apply(1L,701L,"request-001","不需要").getId());
        assertThrows(BusinessException.class,()->sales.apply(1L,701L,"request-001","变更原因"));
        assertThrows(BusinessException.class,()->sales.apply(1L,701L,"request-002","重复申请"));assertOriginal();
    }
    @Test void rejectionUnblocksShippingAndNewApplicationKeepsHistory() {
        var row=sales.apply(1L,701L,"request-001","不需要");assertThrows(BusinessException.class,()->orders.ship(701L,"TRACK"));
        assertThrows(BusinessException.class,()->sales.review(row.getId(),1L,false," "));
        sales.review(row.getId(),1L,false,"已联系买家");var another=sales.apply(1L,701L,"request-002","再次申请");
        assertNotEquals(row.getId(),another.getId());assertEquals(2,sales.history(1L,701L).size());
        sales.review(another.getId(),1L,false,"拒绝原因");orders.ship(701L,"TRACK");assertEquals(1,count("SELECT ship_status FROM `order` WHERE id=701"));
    }
    @Test void successfulRefundUsesActualOrderAmountAndIsExactlyOnce() {
        var row=sales.apply(1L,701L,"request-001","退款");assertEquals(0,new BigDecimal("35").compareTo(row.getAmount()));
        assertTrue(row.getId()>0,"售后编号必须由数据库自增");
        assertEquals(AfterSaleStatus.REFUNDED,sales.review(row.getId(),1L,true,"").getStatus());
        sales.review(row.getId(),1L,true,"");assertEquals(10,count("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(0,count("SELECT sales FROM product WHERE id=100"));assertEquals(2,count("SELECT pay_status FROM `order` WHERE id=701"));
        assertEquals(1,count("SELECT COUNT(*) FROM stock_movement WHERE kind='REFUND_RELEASE'"));assertEquals(1,sales.attempts(row.getId()).size());
        assertTrue(sales.attempts(row.getId()).get(0).getId()>0);
        verify(gateway,times(1)).refund(anyString(),any());assertThrows(BusinessException.class,()->sales.apply(1L,701L,"request-002","再次退款"));
    }
    @Test void historicalLargeIdsSurviveJsonAndCanBeReviewed() throws Exception {
        long historicalId=2114365573985120257L;
        var row=sales.apply(1L,701L,"request-large-id","退款");
        db.update("UPDATE after_sale SET id=? WHERE id=?",historicalId,row.getId());
        var listing=json.readTree(json.writeValueAsString(sales.page(1,10,null,null)));
        var id=listing.path("records").get(0).path("id");
        assertTrue(id.isTextual());assertEquals(Long.toString(historicalId),id.textValue());
        long receivedId=Long.parseLong(id.textValue());
        assertTrue(sales.attempts(receivedId).isEmpty());
        assertEquals(AfterSaleStatus.REFUNDED,sales.review(receivedId,1L,true,"").getStatus());
        sales.review(receivedId,1L,true,"");
        var attempt=sales.attempts(receivedId).get(0);
        db.update("UPDATE refund_attempt SET id=? WHERE id=?",historicalId+1,attempt.getId());
        var attempts=json.readTree(json.writeValueAsString(sales.attempts(receivedId)));
        assertEquals(Long.toString(historicalId+1),attempts.get(0).path("id").textValue());
        assertEquals(Long.toString(historicalId),attempts.get(0).path("afterSaleId").textValue());
        assertEquals(1,count("SELECT COUNT(*) FROM stock_movement WHERE kind='REFUND_RELEASE'"));
        verify(gateway,times(1)).refund(anyString(),any());
    }
    @Test void failedChannelKeepsOrderStockAndRetryCreatesNewAttempt() {
        when(gateway.refund(anyString(),any())).thenReturn(new RefundGateway.Outcome(false,"测试通道失败"),new RefundGateway.Outcome(true,"测试通道恢复"));
        var row=sales.apply(1L,701L,"request-001","退款");assertEquals(AfterSaleStatus.REFUND_FAILED,sales.review(row.getId(),1L,true,"").getStatus());
        assertOriginal();assertThrows(BusinessException.class,()->orders.ship(701L,"TRACK"));
        sales.review(row.getId(),1L,true,"");assertEquals(2,sales.attempts(row.getId()).size());assertEquals(10,count("SELECT stock FROM product_sku WHERE id=9001"));
    }
    @Test void badStockRollsBackReceiptAndOrderTogether() {
        db.update("UPDATE product_sku SET stock=2147483647 WHERE id=9001");var row=sales.apply(1L,701L,"request-001","退款");
        assertThrows(BusinessException.class,()->sales.review(row.getId(),1L,true,""));
        assertEquals(1,count("SELECT pay_status FROM `order` WHERE id=701"));assertEquals(AfterSaleStatus.REQUESTED,sales.history(1L,701L).get(0).getStatus());assertTrue(sales.attempts(row.getId()).isEmpty());
    }
    @Test void concurrentApprovalDoesNotDoubleRefund() throws Exception {
        var row=sales.apply(1L,701L,"request-001","退款");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var a=pool.submit(()->{start.await();return sales.review(row.getId(),1L,true,"");});var b=pool.submit(()->{start.await();return sales.review(row.getId(),2L,true,"");});start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        assertEquals(1,count("SELECT COUNT(*) FROM refund_attempt"));assertEquals(10,count("SELECT stock FROM product_sku WHERE id=9001"));
        assertEquals(0,count("SELECT sales FROM product WHERE id=100"));
        assertEquals(1,count("SELECT COUNT(*) FROM stock_movement WHERE kind='REFUND_RELEASE'"));
        verify(gateway,times(1)).refund(anyString(),any());
    }
    @Test void accountCreationIsAtomicAndPasswordsAreBcrypt() {
        admins.createAccount(new AdminAccountRequest("new_manager","newPassword123","姓名",2L));
        var hash=db.queryForObject("SELECT password FROM admin WHERE username='new_manager'",String.class);assertTrue(hash.startsWith("$2"));
        assertEquals(1,count("SELECT COUNT(*) FROM admin_role WHERE admin_id=(SELECT id FROM admin WHERE username='new_manager')"));
        assertThrows(BusinessException.class,()->admins.createAccount(new AdminAccountRequest("missing_role","newPassword123","",999L)));
        assertEquals(0,count("SELECT COUNT(*) FROM admin WHERE username='missing_role'"));
        admins.setStatus(2L,1L,0);admins.setStatus(2L,1L,1);verify(sessions,times(1)).revokeAll("ADMIN:2");
    }
    @Test void applicationAndShipmentCannotBothSucceed() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var apply=pool.submit(()->{start.await();try{sales.apply(1L,701L,"request-race","退款");return true;}catch(BusinessException e){return false;}});
            var ship=pool.submit(()->{start.await();try{orders.ship(701L,"TRACK");return true;}catch(BusinessException e){return false;}});
            start.countDown();assertNotEquals(apply.get(10,TimeUnit.SECONDS),ship.get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertFalse(count("SELECT ship_status FROM `order` WHERE id=701")==1&&count("SELECT COUNT(*) FROM after_sale")>0);
    }
    @Test void selfAndLastSuperProtectionsAreEnforced() {
        assertThrows(BusinessException.class,()->admins.setStatus(1L,1L,0));
        assertThrows(BusinessException.class,()->admins.resetPassword(1L,1L,"newPassword123"));
        assertThrows(BusinessException.class,()->admins.updateAccount(1L,1L,new AdminAccountUpdateRequest("姓名",2L)));
        assertThrows(BusinessException.class,()->admins.setStatus(1L,99L,0));
        assertThrows(BusinessException.class,()->admins.assignRole(1L,2L));
        admins.resetPassword(2L,1L,"newPassword123");assertNotEquals("original-other",db.queryForObject("SELECT password FROM admin WHERE id=2",String.class));
    }
    @Test void concurrentDemotionsPreserveAnEnabledSuperAdministrator() throws Exception {
        db.update("UPDATE admin_role SET role_id=1 WHERE admin_id=2");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{start.await();try{admins.assignRole(1L,2L);return true;}catch(BusinessException e){return false;}});
            var second=pool.submit(()->{start.await();try{admins.assignRole(2L,2L);return true;}catch(BusinessException e){return false;}});
            start.countDown();assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(1,count("SELECT COUNT(*) FROM admin a JOIN admin_role ar ON ar.admin_id=a.id JOIN role r ON r.id=ar.role_id WHERE r.code='super_admin' AND a.status=1"));
    }
    @Test
    void opposingAdministratorsCannotOverwriteEachOthersFinalReview() throws Exception {
        var application = sales.apply(1L, 701L, "opposing-review", "退款");
        var gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var approve = pool.submit(() -> reviewAtGate(gate, application.getId(), 1L, true));
            var reject = pool.submit(() -> reviewAtGate(gate, application.getId(), 2L, false));
            gate.countDown();
            assertNotEquals(approve.get(10, TimeUnit.SECONDS), reject.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        var finalRow = sales.history(1L, 701L).get(0);
        if (finalRow.getStatus() == AfterSaleStatus.REFUNDED) {
            assertEquals(1L, finalRow.getReviewerId());
            assertEquals(2, count("SELECT pay_status FROM `order` WHERE id=701"));
            assertEquals(10, count("SELECT stock FROM product_sku WHERE id=9001"));
            assertEquals(0, count("SELECT sales FROM product WHERE id=100"));
            assertEquals(1, count("SELECT COUNT(*) FROM refund_attempt"));
            assertEquals(1, count("SELECT COUNT(*) FROM stock_movement WHERE kind='REFUND_RELEASE'"));
            verify(gateway, times(1)).refund(anyString(), any());
        } else {
            assertEquals(AfterSaleStatus.REJECTED, finalRow.getStatus());
            assertEquals(2L, finalRow.getReviewerId());
            assertOriginal();
            assertEquals(0, count("SELECT COUNT(*) FROM refund_attempt"));
            assertEquals(0, count("SELECT COUNT(*) FROM stock_movement"));
            verifyNoInteractions(gateway);
        }
    }

    private boolean reviewAtGate(CountDownLatch gate, Long id, Long operator, boolean approve)
            throws InterruptedException {
        gate.await();
        try {
            sales.review(id, operator, approve, approve ? "同意退款" : "拒绝原因");
            return true;
        } catch (BusinessException failure) {
            assertEquals(409, failure.getCode());
            return false;
        }
    }

}
