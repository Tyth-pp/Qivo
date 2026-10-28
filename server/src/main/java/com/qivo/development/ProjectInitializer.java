package com.qivo.development;

import com.qivo.migration.SkuMigrationPreflight;
import com.qivo.security.Passwords;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** 显式首次初始化入口。不会注册 Spring 组件，也不会启动 Web、Redis或定时任务。 */
public final class ProjectInitializer {
    public enum Outcome { READY, INITIALIZED, SKIPPED }

    private ProjectInitializer() {}

    public static void main(String[] args) {
        try {
            if (args.length < 3 || args.length > 4
                    || !(args[0].equals("inspect") || args[0].equals("initialize"))
                    || (args.length == 4 && !args[3].equals("--transactions")))
                throw new IllegalArgumentException("初始化命令参数无效，请使用 scripts/初始化项目.ps1");
            var settings = ProjectInitializationSettings.load(Path.of(args[1]));
            Outcome outcome = args[0].equals("inspect") ? inspect(settings)
                    : initialize(settings, Path.of(args[2]),
                            System.getenv("QIVO_INIT_ADMIN_PASSWORD"),
                            System.getenv("QIVO_INIT_USER_PASSWORD"), args.length == 4);
            if (outcome == Outcome.SKIPPED) {
                System.out.println("检测到已有业务数据，跳过整次初始化；未执行迁移、修改账号或复制图片。");
                System.exit(10);
            }
            System.out.println(outcome == Outcome.READY
                    ? "目标库 " + settings.database() + " 可首次初始化。"
                    : "首次初始化完成。账号：admin / product_admin / buyer / buyer_other；密码未输出。");
        } catch (IllegalArgumentException exception) {
            System.err.println("初始化参数或开发配置不完整，请检查配置及密码规则；未输出配置内容。");
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("初始化未完成，请检查数据库权限、迁移状态及图片素材；请勿删除已有数据或迁移历史。凭据未输出。");
            System.exit(1);
        }
    }

    /** 仅检查，不建库、不迁移；有任何业务行即跳过。 */
    public static Outcome inspect(ProjectInitializationSettings settings) throws Exception {
        try (Connection connection = settings.connect(false)) {
            if (!schemaExists(connection, settings.database())) return Outcome.READY;
            connection.setCatalog(settings.database());
            List<String> tables = tables(connection);
            if (hasRows(connection, tables)) return Outcome.SKIPPED;
            if (!tables.isEmpty() && !tables.contains("flyway_schema_history"))
                throw new IllegalStateException("目标库已有表但缺少迁移历史，拒绝接管，请核对数据库基线");
            return Outcome.READY;
        }
    }

    public static Outcome initialize(
            ProjectInitializationSettings settings, Path assets,
            String adminPassword, String userPassword, boolean transactions) throws Exception {
        if (inspect(settings) == Outcome.SKIPPED) return Outcome.SKIPPED;
        // 在任何建库/迁移写入前校验密码及素材目录。
        Passwords passwords = new Passwords();
        String adminHash = passwords.encode(adminPassword), userHash = passwords.encode(userPassword);
        CatalogDataInitializer.catalog();
        if (!Files.isDirectory(assets)) throw new IllegalArgumentException("缺少商品封面素材目录");
        Files.createDirectories(settings.upload());
        try (Connection server = settings.connect(false); Statement sql = server.createStatement()) {
            SkuMigrationPreflight.validateServer(server);
            if (!schemaExists(server, settings.database()))
                sql.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + settings.database()
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try (Connection connection = settings.connect(true)) {
            String lock = "qivo:project-init:" + settings.database();
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                statement.setString(1, lock);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) != 1) throw new IllegalStateException("另一个首次初始化命令正在运行");
                }
            }
            try {
                // 再检查一次，避免 inspect 与实际初始化之间已有其他写入。
                if (hasRows(connection, tables(connection))) return Outcome.SKIPPED;
                Flyway.configure().dataSource(settings.url(true), settings.username(), settings.password())
                        .baselineOnMigrate(false).cleanDisabled(true).load().migrate();
                if (hasRows(connection, tables(connection))) return Outcome.SKIPPED;
                List<Path> created = new ArrayList<>();
                connection.setAutoCommit(false);
                try {
                    account(connection, "admin", 1, "admin", adminHash);
                    account(connection, "admin", 2, "product_admin", adminHash);
                    account(connection, "user", 1, "buyer", userHash);
                    account(connection, "user", 2, "buyer_other", userHash);
                    ScriptUtils.executeSqlScript(connection, new EncodedResource(
                            new ClassPathResource("db/development/基础权限.sql"), StandardCharsets.UTF_8));
                    CatalogDataInitializer.seedWithinTransaction(connection, settings.upload(), assets, created);
                    if (transactions) ProjectTransactionSamples.insert(connection);
                    connection.commit();
                    return Outcome.INITIALIZED;
                } catch (Exception exception) {
                    connection.rollback();
                    for (Path image : created) Files.deleteIfExists(image);
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } finally {
                try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    statement.setString(1, lock);
                    statement.executeQuery().close();
                }
            }
        }
    }

    private static boolean schemaExists(Connection connection, String database) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name=?")) {
            statement.setString(1, database);
            try (var rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static List<String> tables(Connection connection) throws Exception {
        List<String> tables = new ArrayList<>();
        try (var rows = connection.getMetaData().getTables(
                connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                String table = rows.getString("TABLE_NAME");
                if (!table.matches("[a-zA-Z0-9_]+"))
                    throw new IllegalStateException("目标库包含无法安全检查的表名");
                tables.add(table);
            }
        }
        return tables;
    }

    private static boolean hasRows(Connection connection, List<String> tables) throws Exception {
        for (String table : tables) {
            if (table.equals("flyway_schema_history")) continue;
            try (Statement sql = connection.createStatement(); var rows = sql.executeQuery(
                    "SELECT 1 FROM `" + table + "` LIMIT 1")) {
                if (rows.next()) return true;
            }
        }
        return false;
    }

    private static void account(Connection connection, String table, long id, String name, String hash)
            throws Exception {
        try (var statement = connection.prepareStatement(
                "INSERT INTO `" + table + "` (id,username,password,status) VALUES (?,?,?,1)")) {
            statement.setLong(1, id); statement.setString(2, name); statement.setString(3, hash);
            statement.executeUpdate();
        }
    }
}
