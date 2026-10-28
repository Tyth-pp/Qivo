package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.qivo.development.ProjectInitializationSettings;
import com.qivo.development.ProjectInitializer;
import com.qivo.development.ProjectInitializer.Outcome;
import com.qivo.security.Passwords;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Comparator;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 只写随机 qivo_init_ 测试库与独立临时图片目录，绝不调用日常库初始化。 */
@EnabledIfSystemProperty(named = "qivo.init.mysql", matches = "true")
class ProjectInitializationMySqlIT {
    private static final String ADMIN_PASSWORD = "FixtureAdmin-2026";
    private static final String USER_PASSWORD = "FixtureBuyer-2026";
    private static final Path ASSETS = Path.of("../assets/qivo-catalog-images").toAbsolutePath().normalize();

    @Test
    void createsFullCatalogWithoutTransactionsAndSkipsRepeatWithoutResettingAnything() throws Exception {
        withDatabase(settings -> {
            assertEquals(Outcome.READY, ProjectInitializer.inspect(settings));
            assertEquals(Outcome.INITIALIZED, initialize(settings, false));
            try (Connection connection = settings.connect(true)) {
                assertEquals(9, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
                assertEquals(40, count(connection, "SELECT COUNT(*) FROM product"));
                assertEquals(55, count(connection, "SELECT COUNT(*) FROM product_sku"));
                assertEquals(55, count(connection, "SELECT COUNT(*) FROM stock_movement"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM admin"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM user"));
                assertEquals(17, count(connection, "SELECT COUNT(*) FROM permission"));
                for (String table : new String[] {"order", "address", "cart", "order_item", "product_comment"})
                    assertEquals(0, count(connection, "SELECT COUNT(*) FROM `" + table + "`"), table);
                try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT password FROM admin WHERE username='admin'")) {
                    rows.next(); assertTrue(new Passwords().matches(ADMIN_PASSWORD, rows.getString(1)));
                }
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("UPDATE product SET name='手工调整的名称' WHERE id=1");
                }
                var before = SkuMigrationIT.fingerprints(connection, SkuMigrationIT.columns(connection));
                assertEquals(Outcome.SKIPPED, ProjectInitializer.initialize(settings, ASSETS, null, null, true));
                assertEquals(before, SkuMigrationIT.fingerprints(connection, SkuMigrationIT.columns(connection)));
            }
        });
    }

    @Test
    void optionalTransactionsHaveSnapshotsAndBalancedStockMovements() throws Exception {
        withDatabase(settings -> {
            assertEquals(Outcome.INITIALIZED, initialize(settings, true));
            try (Connection connection = settings.connect(true)) {
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM `order` WHERE pay_status=1"));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM `order` WHERE ship_status=0"));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM `order` WHERE ship_status=2"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM order_item WHERE sku_snapshot_source='CURRENT'"));
                assertEquals(2, count(connection, "SELECT COUNT(*) FROM stock_movement WHERE kind='ORDER_RESERVE'"));
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM product_sku s WHERE s.stock<>(SELECT SUM(delta) FROM stock_movement m WHERE m.sku_id=s.id)"));
            }
        });
    }

    @Test
    void pictureFailureRollsBackAccountsPermissionsAndCatalogAndAllowsRetry() throws Exception {
        withDatabase(settings -> {
            var first = com.qivo.development.CatalogDataInitializer.catalog().products().get(1);
            Path conflict = settings.upload().resolve("products").resolve(first.image());
            Files.createDirectories(conflict.getParent()); Files.writeString(conflict, "keep-existing-file");
            assertThrows(IllegalStateException.class, () -> initialize(settings, false));
            try (Connection connection = settings.connect(true)) {
                for (String table : new String[] {"admin", "user", "permission", "role", "product", "product_sku"})
                    assertEquals(0, count(connection, "SELECT COUNT(*) FROM `" + table + "`"), table);
            }
            assertEquals("keep-existing-file", Files.readString(conflict));
            try (var files = Files.walk(settings.upload())) { assertEquals(1, files.filter(Files::isRegularFile).count()); }
            Files.delete(conflict);
            assertEquals(Outcome.INITIALIZED, initialize(settings, false));
        });
    }

    @Test
    void populatedV8DatabaseIsSkippedBeforeMigration() throws Exception {
        withDatabase(settings -> {
            createEmpty(settings);
            Flyway.configure().dataSource(url(settings), settings.username(), settings.password()).target("8").load().migrate();
            try (Connection connection = settings.connect(true); var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO role(id,name,code) VALUES(1,'保留角色','keep_role')");
                assertEquals(Outcome.SKIPPED, initialize(settings, true));
                assertEquals(8, count(connection, "SELECT COUNT(*) FROM flyway_schema_history"));
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='role_change_log'"));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM role WHERE code='keep_role'"));
            }
        });
    }

    @Test
    void tablesWithoutMigrationHistoryAreNotAdopted() throws Exception {
        withDatabase(settings -> {
            createEmpty(settings);
            try (Connection connection = settings.connect(true); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE keep_table(id INT PRIMARY KEY)");
                assertThrows(IllegalStateException.class, () -> ProjectInitializer.inspect(settings));
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM keep_table"));
            }
        });
    }

    @Test
    void invalidPasswordCannotCreateDatabase() throws Exception {
        withDatabase(settings -> {
            assertThrows(RuntimeException.class, () -> ProjectInitializer.initialize(settings, ASSETS, "short", USER_PASSWORD, false));
            assertEquals(Outcome.READY, ProjectInitializer.inspect(settings));
            try (Connection connection = settings.connect(false)) {
                assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + settings.database() + "'"));
            }
        });
    }

    private static Outcome initialize(ProjectInitializationSettings settings, boolean samples) throws Exception {
        return ProjectInitializer.initialize(settings, ASSETS, ADMIN_PASSWORD, USER_PASSWORD, samples);
    }

    private static String url(ProjectInitializationSettings settings) {
        return "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    }

    private static void createEmpty(ProjectInitializationSettings settings) throws Exception {
        try (Connection connection = settings.connect(false); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + settings.database() + "`");
        }
    }

    private static int count(Connection connection, String query) throws Exception {
        try (var sql = connection.createStatement(); var rows = sql.executeQuery(query)) {
            rows.next(); return rows.getInt(1);
        }
    }

    private static void withDatabase(Work work) throws Exception {
        var config = SkuMigrationIT.settings();
        String database = "qivo_init_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path upload = Files.createTempDirectory("qivo-init-images-");
        var settings = new ProjectInitializationSettings(config.get("host").toString(),
                Integer.parseInt(config.get("port").toString()), database,
                config.get("username").toString(), config.get("password").toString(), upload);
        try {
            work.run(settings);
        } finally {
            assertTrue(database.matches("qivo_init_[a-f0-9]{12}"));
            try (Connection connection = settings.connect(false); var sql = connection.createStatement()) {
                sql.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
            try (var files = Files.walk(upload)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    @FunctionalInterface private interface Work { void run(ProjectInitializationSettings settings) throws Exception; }
}
