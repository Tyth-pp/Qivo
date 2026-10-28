package com.qivo.development;

import com.qivo.security.Passwords;

import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 独立运行的开发数据初始化命令，不注册为 Spring 组件，不随应用启动执行。 */
public final class DevelopmentDataInitializer {
    private static final Set<String> BLOCKED_DATABASES =
            Set.of("mall", "mysql", "information_schema", "performance_schema", "sys");
    private static final List<String> TABLES =
            List.of(
                    "user",
                    "admin",
                    "role",
                    "permission",
                    "admin_role",
                    "role_permission",
                    "product_category",
                    "product",
                    "product_img",
                    "cart",
                    "address",
                    "order",
                    "order_item",
                    "banner",
                    "product_collect",
                    "product_comment");

    private DevelopmentDataInitializer() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("初始化命令不接受命令行凭据，请使用环境变量");
        String url = required("QIVO_DEV_DB_URL");
        String user = required("DB_USERNAME");
        String password = required("DB_PASSWORD");
        String target = required("QIVO_DEV_DATABASE_CONFIRM");
        Map<String, String> passwords =
                Map.of(
                        "admin", required("QIVO_DEV_ADMIN_PASSWORD"),
                        "product_admin", required("QIVO_DEV_OPERATOR_PASSWORD"),
                        "buyer", required("QIVO_DEV_USER_PASSWORD"),
                        "buyer_other", required("QIVO_DEV_USER_PASSWORD"));
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            validateTarget(connection, target);
            new Passwords().encode(passwords.get("admin"));
            new Passwords().encode(passwords.get("product_admin"));
            new Passwords().encode(passwords.get("buyer"));
            Flyway.configure()
                    .dataSource(url, user, password)
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .load()
                    .migrate();
            initialize(connection, target, passwords);
        }
        System.out.println("开发数据初始化完成；密码由环境变量提供，未在控制台输出。");
    }

    public static void initialize(
            Connection connection, String target, Map<String, String> passwords) throws Exception {
        validateTarget(connection, target);
        boolean autoCommit = connection.getAutoCommit();
        if (!autoCommit) throw new IllegalStateException("初始化需要独立连接，不得加入已有事务");
        String lock = "qivo:development-seed:" + target;
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, lock);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1)
                    throw new IllegalStateException("另一个初始化命令正在运行");
            }
        }
        try {
            connection.setAutoCommit(false);
            for (String table : TABLES) {
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
                    rows.next();
                    if (rows.getLong(1) != 0)
                        throw new IllegalStateException("开发库已有业务数据，拒绝覆盖或重设密码：" + table);
                }
            }
            boolean skuModel;
            try (var tables =
                    connection
                            .getMetaData()
                            .getTables(
                                    connection.getCatalog(),
                                    null,
                                    "product_sku",
                                    new String[] {"TABLE"})) {
                skuModel = tables.next();
            }
            if (skuModel) {
                try (var statement = connection.createStatement();
                        var rows = statement.executeQuery("SELECT COUNT(*) FROM product_sku")) {
                    rows.next();
                    if (rows.getLong(1) != 0) throw new IllegalStateException("SKU 表非空，拒绝初始化");
                }
            }
            Passwords encoder = new Passwords();
            account(connection, "admin", 1, "admin", encoder.encode(passwords.get("admin")));
            account(
                    connection,
                    "admin",
                    2,
                    "product_admin",
                    encoder.encode(passwords.get("product_admin")));
            account(connection, "user", 1, "buyer", encoder.encode(passwords.get("buyer")));
            account(
                    connection,
                    "user",
                    2,
                    "buyer_other",
                    encoder.encode(passwords.get("buyer_other")));
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(
                            new ClassPathResource("db/development/开发演示数据.sql"),
                            StandardCharsets.UTF_8));
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(
                            new ClassPathResource(
                                    skuModel
                                            ? "db/development/开发SKU交易演示数据.sql"
                                            : "db/development/开发历史交易演示数据.sql"),
                            StandardCharsets.UTF_8));
            try (var tables =
                    connection
                            .getMetaData()
                            .getTables(
                                    connection.getCatalog(),
                                    null,
                                    "stock_movement",
                                    new String[] {"TABLE"})) {
                if (tables.next())
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate(
                                "INSERT INTO"
                                    + " stock_movement(product_id,sku_id,event_key,kind,delta,stock_before,stock_after,actor_domain)"
                                    + " SELECT"
                                    + " product_id,id,CONCAT('initial:',id),'INITIAL',stock,0,stock,'SYSTEM'"
                                    + " FROM product_sku");
                    }
            }
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.executeQuery().close();
            }
        }
    }

    private static void validateTarget(Connection connection, String target) throws Exception {
        if (target == null
                || !target.matches("[a-zA-Z0-9_]+")
                || BLOCKED_DATABASES.contains(target.toLowerCase(Locale.ROOT))
                || !Objects.equals(connection.getCatalog(), target))
            throw new IllegalArgumentException("开发库名必须合法且与确认值一致，禁止操作历史 mall 库或 MySQL 系统库");
    }

    private static void account(
            Connection connection, String table, long id, String name, String hash)
            throws Exception {
        try (var statement =
                connection.prepareStatement(
                        "INSERT INTO `"
                                + table
                                + "` (id,username,password,status) VALUES (?,?,?,1)")) {
            statement.setLong(1, id);
            statement.setString(2, name);
            statement.setString(3, hash);
            statement.executeUpdate();
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少环境变量：" + key);
        return value;
    }
}
