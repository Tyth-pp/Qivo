package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.qivo.development.DevelopmentDataInitializer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/** 必须显式开启；仅在随机 qivo_sku_*_tx 库写入，不使用真实 Redis。 */
@SpringBootTest(
        properties = {
            "spring.sql.init.mode=never",
            "spring.flyway.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=8"
        })
@EnabledIfSystemProperty(named = "qivo.transaction.mysql", matches = "true")
class TransactionConsistencyIT extends TransactionConsistencyTests {
    private static String base, options, username, password, database, source;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        var settings = SkuMigrationIT.settings();
        source = settings.get("database").toString();
        assertEquals("qivo", source, "仅允许明确的 qivo 本机配置");
        assertEquals(source, System.getProperty("qivo.sku.source"));
        base = "jdbc:mysql://" + settings.get("host") + ":" + settings.get("port") + "/";
        options = "?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        username = settings.get("username").toString();
        password = settings.get("password").toString();
        database =
                "qivo_sku_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        + "_tx";
        try (var connection =
                        DriverManager.getConnection(base + source + options, username, password);
                var sql = connection.createStatement()) {
            sql.execute(
                    "CREATE DATABASE `"
                            + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String url = base + database + options;
        try {
            Flyway.configure().dataSource(url, username, password).target("5").load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password)) {
                DevelopmentDataInitializer.initialize(
                        connection,
                        database,
                        Map.of(
                                "admin",
                                "txAdmin123",
                                "product_admin",
                                "txOperator123",
                                "buyer",
                                "txBuyer123",
                                "buyer_other",
                                "txOther123"));
                var columns = SkuMigrationIT.columns(connection);
                var before = SkuMigrationIT.fingerprints(connection, columns);
                Flyway.configure().dataSource(url, username, password).load().migrate();
                assertEquals(
                        before, SkuMigrationIT.fingerprints(connection, columns), "V6 不得改写原表业务数据");
                try (var sql = connection.createStatement();
                        var rows =
                                sql.executeQuery(
                                        "SELECT COUNT(*) FROM product_sku s LEFT JOIN"
                                            + " stock_movement m ON m.sku_id=s.id AND"
                                            + " m.kind='INITIAL' WHERE m.id IS NULL OR"
                                            + " m.stock_before<>0 OR m.delta<>s.stock OR"
                                            + " m.stock_after<>s.stock")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getLong(1));
                }
                try (var sql = connection.createStatement()) {
                    assertThrows(
                            SQLException.class,
                            () ->
                                    sql.executeUpdate(
                                            "INSERT INTO"
                                                + " stock_movement(product_id,sku_id,event_key,kind,delta,stock_before,stock_after,actor_domain)"
                                                + " VALUES(1,1,'broken-check','MANUAL_ADJUST',1,0,2,'SYSTEM')"));
                }
            }
        } catch (Exception | AssertionError failure) {
            cleanup();
            throw failure;
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (database == null) return;
        if (!database.matches("qivo_sku_[a-f0-9]{12}_tx") || database.equals(source))
            throw new IllegalStateException("拒绝清理未确认的临时库");
        try (Connection connection =
                        DriverManager.getConnection(base + source + options, username, password);
                var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }
}
