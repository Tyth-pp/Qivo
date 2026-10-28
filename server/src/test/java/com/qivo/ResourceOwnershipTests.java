package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.qivo.cache.RedisStore;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

/** 使用真实控制器、鉴权和 SQL 核对横向越权，Redis 会话仅在测试中隔离。 */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:ownership;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
    "spring.sql.init.schema-locations=classpath:account-test-schema.sql,classpath:ownership-test-schema.sql,classpath:test-schema.sql,classpath:transaction-test-schema.sql"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ResourceOwnershipTests {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean RedisStore redis;
    @MockitoBean SessionRegistry sessions;

    @BeforeEach
    void seedOwnership() {
        for (String table : List.of("refund_attempt","after_sale","product_comment","product_collect",
                "order_item","`order`","cart","product_sku","product","address","product_category","`user`")) {
            db.update("DELETE FROM " + table);
        }
        db.update("INSERT INTO `user`(id,username,password,status,phone) VALUES(1,'owner','test-only',1,'13800000001'),(2,'attacker','test-only',1,'13800000002')");
        db.update("INSERT INTO product_category(id,name) VALUES(1,'隔离分类')");
        db.update("INSERT INTO product(id,name,price,status,sales,category_id) VALUES(100,'隔离商品',13,1,3,1)");
        db.update("INSERT INTO product_sku(id,product_id,sku_code,price,stock,status,spec_key,spec_values) VALUES(9001,100,'OWNERSHIP-SKU',13,7,'ENABLED','DEFAULT','[]')");
        db.update("INSERT INTO address(id,user_id,receiver_name,receiver_phone,province,city,district,detail,is_default) VALUES(11,1,'原收件人','13800000001','省','市','区','原地址',1),(12,2,'本人','13800000002','省','市','区','本人地址',1)");
        db.update("INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,create_time,address_id) VALUES(701,'OWN-701',1,39,1,0,NOW(),11)");
        db.update("INSERT INTO order_item(id,order_id,product_id,sku_id,sku_code,product_name,quantity,unit_price,total_price,sku_snapshot_source) VALUES(801,701,100,9001,'OWNERSHIP-SKU','购买快照',3,13,39,'CURRENT')");
        db.update("INSERT INTO cart(id,user_id,product_id,sku_id,quantity,checked) VALUES(21,1,100,9001,1,1)");
        db.update("INSERT INTO product_comment(id,user_id,product_id,order_id,rating,content) VALUES(31,1,100,701,5,'原评价')");
        db.update("INSERT INTO product_collect(user_id,product_id) VALUES(1,100)");
        when(sessions.version(anyString())).thenReturn("0");
        when(sessions.active(anyString(),anyString())).thenReturn(true);
    }

    String token(long id) {
        return "Bearer " + jwt.generateToken(id,id==1?"owner":"attacker","USER","test-only");
    }
    int number(String sql) { return db.queryForObject(sql,Integer.class); }

    @Test
    void foreignOrderCannotBeReadPaidCancelledOrConfirmed() throws Exception {
        var auth=token(2);
        http.perform(get("/api/fore/order/detail/701").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(post("/api/fore/order/pay").header("Authorization",auth).contentType("application/json")
                .content("{\"orderId\":701,\"userId\":1}")).andExpect(status().isNotFound());
        http.perform(put("/api/fore/order/cancel/701").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(put("/api/fore/order/confirm/701").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(get("/api/fore/order/list?userId=1").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        http.perform(get("/api/fore/order/detail/701").header("Authorization",token(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.order.userId").value(1));
        assertEquals(1,number("SELECT pay_status FROM `order` WHERE id=701"));
        assertEquals(0,number("SELECT ship_status FROM `order` WHERE id=701"));
        assertEquals(7,number("SELECT stock FROM product_sku WHERE id=9001"));
    }

    @Test
    void foreignAddressCannotBeEditedDeletedOrMadeDefaultBySpoofingOwner() throws Exception {
        var auth=token(2);
        http.perform(put("/api/fore/user/address/11").header("Authorization",auth).contentType("application/json")
                .content("{\"id\":12,\"userId\":1,\"receiverName\":\"篡改\",\"isDefault\":1}"))
                .andExpect(status().isNotFound());
        http.perform(delete("/api/fore/user/address/11").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(get("/api/fore/user/address?userId=1").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(12));
        assertEquals("原收件人",db.queryForObject("SELECT receiver_name FROM address WHERE id=11",String.class));
        assertEquals(1,number("SELECT is_default FROM address WHERE id=12"));
    }

    @Test
    void foreignCartCannotBeChangedAndSubmittedOwnerIsIgnored() throws Exception {
        var auth=token(2);
        http.perform(put("/api/fore/cart/update").header("Authorization",auth).contentType("application/json")
                .content("{\"cartId\":21,\"quantity\":9,\"userId\":1}")).andExpect(status().isNotFound());
        http.perform(put("/api/fore/cart/check").header("Authorization",auth).contentType("application/json")
                .content("{\"cartId\":21,\"checked\":0,\"userId\":1}")).andExpect(status().isNotFound());
        http.perform(delete("/api/fore/cart/delete/21").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(get("/api/fore/cart/list?userId=1").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        assertEquals(1,number("SELECT quantity FROM cart WHERE id=21"));
        assertEquals(1,number("SELECT checked FROM cart WHERE id=21"));
    }

    @Test
    void foreignAfterSaleAndReviewCannotBeCreatedOrRead() throws Exception {
        var auth=token(2);
        http.perform(post("/api/fore/after-sale").header("Authorization",auth).header("Idempotency-Key","ownership-key")
                .contentType("application/json").content("{\"orderId\":701,\"reason\":\"退款\",\"userId\":1}"))
                .andExpect(status().isNotFound());
        http.perform(get("/api/fore/after-sale/order/701").header("Authorization",auth)).andExpect(status().isNotFound());
        http.perform(post("/api/fore/comment/add").header("Authorization",auth).contentType("application/json")
                .content("{\"orderId\":701,\"productId\":100,\"userId\":1,\"rating\":5,\"content\":\"冒名\"}"))
                .andExpect(status().isNotFound());
        http.perform(post("/api/fore/comment/append").header("Authorization",auth).contentType("application/json")
                .content("{\"commentId\":31,\"appendContent\":\"冒名追评\",\"userId\":1}"))
                .andExpect(status().isConflict());
        assertEquals(0,number("SELECT COUNT(*) FROM after_sale"));
        assertEquals(1,number("SELECT COUNT(*) FROM product_comment"));
        assertNull(db.queryForObject("SELECT append_content FROM product_comment WHERE id=31",String.class));
    }

    @Test
    void profileAndCollectionAlwaysUseAuthenticatedOwner() throws Exception {
        var auth=token(2);
        http.perform(put("/api/fore/user/profile").header("Authorization",auth).contentType("application/json")
                .content("{\"id\":1,\"phone\":\"13900000002\",\"status\":0,\"score\":9999,\"password\":\"injected\"}"))
                .andExpect(status().isOk());
        assertEquals("13800000001",db.queryForObject("SELECT phone FROM `user` WHERE id=1",String.class));
        assertEquals("13900000002",db.queryForObject("SELECT phone FROM `user` WHERE id=2",String.class));
        assertEquals("test-only",db.queryForObject("SELECT password FROM `user` WHERE id=2",String.class));
        assertEquals(1,number("SELECT status FROM `user` WHERE id=2"));
        http.perform(delete("/api/fore/product/collect/100").header("Authorization",auth)).andExpect(status().isOk());
        http.perform(get("/api/fore/product/collect?userId=1").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        http.perform(post("/api/fore/product/collect").header("Authorization",auth).contentType("application/json")
                .content("{\"productId\":100,\"userId\":1}")).andExpect(status().isOk());
        assertEquals(1,number("SELECT COUNT(*) FROM product_collect WHERE user_id=1"));
        assertEquals(1,number("SELECT COUNT(*) FROM product_collect WHERE user_id=2"));
    }
}
