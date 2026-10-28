package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.qivo.development.CatalogDataInitializer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 显式 MySQL 验收：随机空库、日常数据只读副本；不写入日常业务表。 */
@EnabledIfSystemProperty(named = "qivo.demo.mysql", matches = "true")
class DemoCatalogInitializerIT {
    @Test
    void freshCatalogIsRepeatableAndCleanupProtectsReferences() throws Exception {
        withDatabase(false, (connection, database, upload) -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO product_category(name,parent_id,status) VALUES ('保留分类',0,1)");
            }
            seed(connection, database, upload);
            seed(connection, database, upload);
            assertEquals(40, count(connection, "SELECT COUNT(*) FROM product"));
            assertEquals(55, count(connection, "SELECT COUNT(*) FROM product_sku"));
            assertEquals(55, count(connection, "SELECT COUNT(*) FROM stock_movement"));
            assertEquals(15, count(connection, "SELECT COUNT(*) FROM product WHERE spec_mode='MULTI'"));
            assertEquals(5, count(connection, "SELECT COUNT(*) FROM product WHERE status=0"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM product WHERE name LIKE '%演示%' OR description LIKE '%演示%'"));
            assertEquals(5, count(connection, "SELECT COUNT(*) FROM (SELECT category_id FROM product GROUP BY category_id HAVING COUNT(*)=8) x"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE product_sku SET price=75.00 WHERE sku_code='QIVO-CATALOG-V2-FELT-TRAY-STANDARD'");
            }
            seed(connection, database, upload);
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM product_sku WHERE sku_code='QIVO-CATALOG-V2-FELT-TRAY-STANDARD' AND price=75.00"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO cart(user_id,product_id,sku_id,quantity,checked) SELECT 999,product_id,id,1,1 FROM product_sku WHERE sku_code='QIVO-CATALOG-V2-FELT-TRAY-STANDARD'");
            }
            assertThrows(IllegalStateException.class, () -> CatalogDataInitializer.run(connection, database, upload, assets(), "cleanup"));
            assertEquals(40, count(connection, "SELECT COUNT(*) FROM product"));
            try (var statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM cart WHERE user_id=999"); }
            CatalogDataInitializer.run(connection, database, upload, assets(), "cleanup");
            CatalogDataInitializer.run(connection, database, upload, assets(), "cleanup");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM product"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM product_category WHERE name='保留分类'"));
        });
    }

    @Test
    void actualDataCopyPreservesAllExistingBusinessValues() throws Exception {
        withDatabase(true, (connection, database, upload) -> {
            Map<String, List<List<String>>> before = new LinkedHashMap<>();
            for (String table : tables(connection)) {
                if (!List.of("product", "product_category", "stock_movement", "flyway_schema_history").contains(table))
                    before.put(table, snapshot(connection, table, "*"));
            }
            var products = snapshot(connection, "product", "id,name,description,price,original_price,stock,status,main_image,sales,spec_mode,spec_definition");
            var stock = snapshot(connection, "stock_movement", "*");
            seed(connection, database, upload);
            seed(connection, database, upload);
            assertEquals(40, count(connection, "SELECT COUNT(*) FROM product"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM product_category WHERE parent_id=0"));
            assertEquals(5, count(connection, "SELECT COUNT(*) FROM (SELECT category_id FROM product GROUP BY category_id HAVING COUNT(*)=8) x"));
            assertTrue(snapshot(connection, "product", "id,name,description,price,original_price,stock,status,main_image,sales,spec_mode,spec_definition").containsAll(products), "已有商品的非分类字段保持原值");
            assertTrue(snapshot(connection, "stock_movement", "*").containsAll(stock), "原有库存记录保持原值");
            for (var entry : before.entrySet())
                assertTrue(snapshot(connection, entry.getKey(), "*").containsAll(entry.getValue()), entry.getKey() + " 原有记录保持原值");
        });
    }

    private static void seed(Connection connection, String database, Path upload) throws Exception {
        CatalogDataInitializer.run(connection, database, upload, assets(), "seed");
    }
    private static Path assets() { return Path.of("../assets/qivo-catalog-images").toAbsolutePath().normalize(); }

    private static void withDatabase(boolean copy, Work work) throws Exception {
        var settings = SkuMigrationIT.settings();
        assertEquals("qivo", settings.get("database"));
        String database = "qivo_catalog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String base = "jdbc:mysql://" + settings.get("host") + ":" + settings.get("port") + "/";
        String options = "?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user = settings.get("username").toString(), password = settings.get("password").toString();
        Path upload = Files.createTempDirectory("qivo-catalog-upload-");
        try {
            try (var admin = DriverManager.getConnection(base + "qivo" + options, user, password); var statement = admin.createStatement()) {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            String url = base + database + options;
            Flyway.configure().dataSource(url, user, password).load().migrate();
            try (var connection = DriverManager.getConnection(url, user, password)) {
                if (copy) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("SET FOREIGN_KEY_CHECKS=0");
                        try {
                            for (String table : tables(connection)) {
                                if (!table.equals("flyway_schema_history")) statement.executeUpdate("INSERT INTO `" + table + "` SELECT * FROM qivo.`" + table + "`");
                            }
                        } finally { statement.execute("SET FOREIGN_KEY_CHECKS=1"); }
                    }
                }
                work.run(connection, database, upload);
            }
        } finally {
            assertTrue(database.matches("qivo_catalog_[a-f0-9]{12}"));
            try (var admin = DriverManager.getConnection(base + "qivo" + options, user, password); var statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
            try (var files = Files.walk(upload)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static List<String> tables(Connection connection) throws Exception {
        List<String> result = new ArrayList<>();
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (!name.matches("[a-z_]+")) throw new IllegalStateException("非法表名");
                result.add(name);
            }
        }
        return result;
    }
    private static List<List<String>> snapshot(Connection connection, String table, String fields) throws Exception {
        List<List<String>> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT " + fields + " FROM `" + table + "` ORDER BY id")) {
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }
    private static int count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { rows.next(); return rows.getInt(1); }
    }
    @FunctionalInterface private interface Work { void run(Connection connection, String database, Path upload) throws Exception; }
}
