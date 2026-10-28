package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.qivo.config.MyBatisConfig;
import com.qivo.constant.SkuConstant;
import com.qivo.development.DevelopmentDataInitializer;
import com.qivo.entity.ProductSku;
import com.qivo.enums.ProductSpecMode;
import com.qivo.enums.SkuStatus;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.migration.SkuMigrationPreflight;
import com.qivo.service.impl.ProductSkuServiceImpl;
import com.qivo.vo.SkuProductSummaryVO;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** 显式 SKU A 批演练：源库只读，草案只运行在本轮随机副本及随机空库。 */
class SkuMigrationIT {
    @Test
    void cloneBackfillConstraintsAndSkuReadModelsAreVerifiedWithoutChangingSource()
            throws Exception {
        assertTrue(Boolean.getBoolean("qivo.sku.rehearsal"), "必须显式开启SKU隔离演练");
        Map<String, Object> config = settings();
        String source = config.get("database").toString();
        assertEquals(source, System.getProperty("qivo.sku.source"), "必须明确确认只读源库名");
        assertTrue(source.matches("[a-zA-Z0-9_]+"));
        assertNotEquals("mall", source.toLowerCase());
        String base = "jdbc:mysql://" + config.get("host") + ":" + config.get("port") + "/";
        String options =
                "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user = config.get("username").toString(),
                password = config.get("password").toString();
        String copy = "qivo_sku_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fresh = copy + "_fresh";
        List<String> created = new ArrayList<>();
        Map<String, List<String>> columns;
        Map<String, String> before;
        try (Connection admin =
                        DriverManager.getConnection(base + source + options, user, password);
                Connection reader =
                        DriverManager.getConnection(base + source + options, user, password)) {
            columns = columns(reader);
            assertTrue(columns.size() == 17 || columns.size() == 18, "源库应为商城及迁移历史表");
            int sourceVersion;
            try (var statement = reader.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT MAX(CAST(version AS UNSIGNED)) FROM"
                                            + " flyway_schema_history WHERE success=1")) {
                rows.next();
                sourceVersion = rows.getInt(1);
                assertTrue(sourceVersion == 3 || sourceVersion == 5, "只支持明确的 V3/V5 基线");
            }
            try {
                create(admin, copy);
                created.add(copy);
                create(admin, fresh);
                created.add(fresh);
                try (Connection writer =
                        DriverManager.getConnection(base + copy + options, user, password)) {
                    for (String table : columns.keySet()) {
                        try (var statement = writer.createStatement()) {
                            statement.execute(
                                    "CREATE TABLE `"
                                            + table
                                            + "` LIKE `"
                                            + source
                                            + "`.`"
                                            + table
                                            + "`");
                        }
                    }
                    reader.setReadOnly(true);
                    reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    reader.setAutoCommit(false);
                    before = fingerprints(reader, columns);
                    writer.setAutoCommit(false);
                    for (var entry : columns.entrySet())
                        copyRows(reader, writer, entry.getKey(), entry.getValue());
                    writer.commit();
                    reader.rollback();
                }
                try (Connection c =
                        DriverManager.getConnection(base + copy + options, user, password)) {
                    assertEquals(before, fingerprints(c, columns));
                    if (sourceVersion == 3) {
                        SkuMigrationPreflight.validate(c);
                        c.setAutoCommit(false);
                        try (var statement = c.createStatement()) {
                            statement.executeUpdate(
                                    "UPDATE product SET price=0 WHERE id=(SELECT id FROM (SELECT"
                                            + " MIN(id) id FROM product) p)");
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> SkuMigrationPreflight.validate(c));
                            assertFalse(tableExists(c, "product_sku"));
                        } finally {
                            c.rollback();
                            c.setAutoCommit(true);
                        }
                        c.setAutoCommit(false);
                        try (var statement = c.createStatement()) {
                            statement.executeUpdate(
                                    "INSERT INTO cart(user_id,product_id,quantity,checked)"
                                            + " VALUES(1,9223372036854775806,1,1)");
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> SkuMigrationPreflight.validate(c));
                        } finally {
                            c.rollback();
                            c.setAutoCommit(true);
                        }
                        SkuMigrationPreflight.validate(c);
                        Flyway draft = flyway(base + copy + options, user, password, true);
                        assertEquals(2, draft.migrate().migrationsExecuted);
                        assertEquals(0, draft.migrate().migrationsExecuted);
                        assertEquals(before, fingerprints(c, columns), "原有列及行数据不得被迁移改写");
                        verifyBackfill(c);
                    }
                }
                String freshUrl = base + fresh + options;
                assertEquals(
                        3, flyway(freshUrl, user, password, false).migrate().migrationsExecuted);
                try (Connection c = DriverManager.getConnection(freshUrl, user, password)) {
                    DevelopmentDataInitializer.initialize(
                            c,
                            fresh,
                            Map.of(
                                    "admin",
                                    "skuAdmin123",
                                    "product_admin",
                                    "skuOperator123",
                                    "buyer",
                                    "skuBuyer123",
                                    "buyer_other",
                                    "skuBuyer123"));
                    var freshColumns = columns(c);
                    var freshBefore = fingerprints(c, freshColumns);
                    SkuMigrationPreflight.validate(c);
                    assertEquals(
                            2, flyway(freshUrl, user, password, true).migrate().migrationsExecuted);
                    assertEquals(freshBefore, fingerprints(c, freshColumns));
                    verifyBackfill(c);
                    verifyConstraints(c);
                }
                verifyReadModels(freshUrl, user, password);
                try (Connection check =
                        DriverManager.getConnection(base + source + options, user, password)) {
                    assertEquals(before, fingerprints(check, columns), "演练结束源库仍须完全一致");
                    assertEquals(
                            sourceVersion == 5, tableExists(check, "product_sku"), "运行库结构不得被演练修改");
                }
            } finally {
                for (String database : created) {
                    assertTrue(database.matches("qivo_sku_[a-f0-9]{12}(_fresh)?"));
                    assertNotEquals(source, database);
                    try (var statement = admin.createStatement()) {
                        statement.execute("DROP DATABASE `" + database + "`");
                    }
                }
            }
        }
    }

    private static void verifyBackfill(Connection c) throws Exception {
        assertEquals(
                0,
                count(
                        c,
                        "SELECT COUNT(*) FROM product p LEFT JOIN product_sku s ON"
                            + " s.product_id=p.id AND s.spec_key='DEFAULT' WHERE s.id IS NULL OR"
                            + " s.price<>p.price OR NOT(s.original_price <=> p.original_price) OR"
                            + " s.stock<>p.stock OR s.sku_code<>CONCAT('QIVO-DEFAULT-',p.id)"));
        assertEquals(
                count(c, "SELECT COUNT(*) FROM product"),
                count(c, "SELECT COUNT(*) FROM product_sku"));
        assertEquals(
                0,
                count(
                        c,
                        "SELECT COUNT(*) FROM product_sku WHERE spec_key<>'DEFAULT' OR"
                                + " JSON_LENGTH(spec_values)<>0"));
        assertEquals(
                0,
                count(
                        c,
                        "SELECT COUNT(*) FROM cart c JOIN product_sku s ON s.id=c.sku_id WHERE"
                                + " c.product_id<>s.product_id"));
        assertEquals(
                0,
                count(
                        c,
                        "SELECT COUNT(*) FROM order_item WHERE sku_id IS NULL OR sku_code IS NULL"
                                + " OR sku_spec_snapshot IS NOT NULL OR"
                                + " sku_snapshot_source<>'LEGACY'"));
        assertEquals(
                0,
                count(
                        c,
                        "SELECT COUNT(*) FROM `order` WHERE receiver_name IS NOT NULL OR"
                            + " receiver_phone IS NOT NULL OR receiver_address_snapshot IS NOT NULL"
                            + " OR address_snapshot_source<>'LEGACY'"));
        try (var statement = c.createStatement();
                var rows =
                        statement.executeQuery(
                                "SELECT sku_code FROM product_sku ORDER BY product_id LIMIT 1")) {
            assertTrue(rows.next());
            assertTrue(rows.getString(1).startsWith(SkuConstant.DEFAULT_CODE_PREFIX));
        }
    }

    private static void verifyConstraints(Connection c) throws Exception {
        try (var statement = c.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO"
                                        + " product_sku(product_id,sku_code,spec_key,spec_values,price,stock)"
                                        + " VALUES(1,'DUPLICATE','DEFAULT',JSON_ARRAY(),1,0)"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO"
                                        + " product_sku(product_id,sku_code,spec_key,spec_values,price,stock)"
                                        + " VALUES(1,'NEGATIVE','negative',JSON_ARRAY(),1,-1)"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO"
                                        + " product_sku(product_id,sku_code,spec_key,spec_values,price,stock)"
                                        + " VALUES(1,'ZERO-PRICE','zero',JSON_ARRAY(),0,0)"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO"
                                        + " product_sku(product_id,sku_code,spec_key,spec_values,price,stock)"
                                        + " VALUES(1,'BAD-JSON','bad',JSON_OBJECT(),1,0)"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "UPDATE cart SET sku_id=(SELECT id FROM product_sku WHERE"
                                            + " product_id=2) WHERE product_id=1"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "UPDATE order_item SET sku_id=(SELECT id FROM product_sku WHERE"
                                            + " product_id=3) WHERE product_id=2"));
            assertThrows(
                    SQLException.class,
                    () ->
                            statement.executeUpdate(
                                    "INSERT INTO cart(user_id,product_id,sku_id,quantity) SELECT"
                                            + " user_id,product_id,sku_id,1 FROM cart LIMIT 1"));
        }
    }

    private static void verifyReadModels(String url, String user, String password)
            throws Exception {
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl(url);
            ds.setUsername(user);
            ds.setPassword(password);
            ds.setMaximumPoolSize(2);
            ds.setMinimumIdle(0);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setMapperLocations(
                    new PathMatchingResourcePatternResolver()
                            .getResources("classpath:mapper/*.xml"));
            factory.setPlugins(new MyBatisConfig().mybatisPlusInterceptor());
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            try (var session = Objects.requireNonNull(factory.getObject()).openSession(false)) {
                ProductSkuMapper mapper = session.getMapper(ProductSkuMapper.class);
                var service = new ProductSkuServiceImpl(mapper);
                var original = service.page(1, 10, null, null, 1, null);
                assertEquals(4, original.getTotal());
                assertTrue(
                        original.getRecords().stream()
                                .allMatch(
                                        row ->
                                                row.getDefaultSkuId() != null
                                                        && row.getSpecMode()
                                                                == ProductSpecMode.SINGLE));
                try (var statement = session.getConnection().createStatement()) {
                    statement.executeUpdate(
                            "UPDATE product_sku SET status='DISABLED' WHERE product_id IN(1,2)");
                    statement.executeUpdate(
                            "UPDATE product SET spec_mode='MULTI',"
                                + " spec_definition=JSON_ARRAY(JSON_OBJECT('id','color','name','颜色','values',JSON_ARRAY(JSON_OBJECT('id','v0','name','黑色'),JSON_OBJECT('id','v1','name','白色'),JSON_OBJECT('id','v2','name','灰色'))))"
                                + " WHERE id=1");
                    statement.executeUpdate("UPDATE product_sku SET price=9 WHERE product_id=3");
                }
                for (int i = 0; i < 3; i++) {
                    ProductSku sku = new ProductSku();
                    sku.setProductId(1L);
                    sku.setSkuCode("READ-" + i);
                    sku.setSpecKey("color=v" + i);
                    sku.setSpecValues("[{\"specId\":\"color\",\"valueId\":\"v" + i + "\"}]");
                    sku.setPrice(new BigDecimal(i == 0 ? "9" : i == 1 ? "10" : "12"));
                    sku.setStock(i == 0 ? 0 : 1_500_000_000);
                    sku.setStatus(SkuStatus.ENABLED);
                    assertEquals(1, mapper.insert(sku));
                    assertNotNull(sku.getId());
                    assertEquals(SkuStatus.ENABLED, mapper.selectById(sku.getId()).getStatus());
                }
                var page = service.page(1, 1, null, null, 1, "price_asc");
                assertEquals(3, page.getTotal());
                SkuProductSummaryVO row = page.getRecords().get(0);
                assertEquals(1L, row.getProductId());
                assertEquals(ProductSpecMode.MULTI, row.getSpecMode());
                assertEquals(0, new BigDecimal("9").compareTo(row.getMinPrice()));
                assertEquals(0, new BigDecimal("12").compareTo(row.getMaxPrice()));
                assertEquals(3_000_000_000L, row.getTotalStock());
                assertEquals(3, row.getEnabledSkuCount());
                assertNull(row.getDefaultSkuId());
                assertEquals(
                        3L,
                        service.page(2, 1, null, null, 1, "price_asc")
                                .getRecords()
                                .get(0)
                                .getProductId());
                assertEquals(
                        4L,
                        service.page(1, 1, null, null, 1, "price_desc")
                                .getRecords()
                                .get(0)
                                .getProductId());
                assertEquals(1, service.page(1, 10, null, 1L, 1, null).getTotal());
                assertEquals(1, service.page(1, 10, "待上架", null, 0, null).getTotal());
                session.rollback();
            }
        }
    }

    private static Flyway flyway(String url, String user, String password, boolean draft) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .target(draft ? "5" : "3")
                .load();
    }

    private static void create(Connection c, String database) throws Exception {
        try (var statement = c.createStatement()) {
            statement.execute(
                    "CREATE DATABASE `"
                            + database
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static boolean tableExists(Connection c, String table) throws Exception {
        try (var statement =
                c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND"
                                + " table_name=?")) {
            statement.setString(1, c.getCatalog());
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) != 0;
            }
        }
    }

    private static long count(Connection c, String sql) throws Exception {
        try (var statement = c.createStatement();
                var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    static Map<String, List<String>> columns(Connection c) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        try (var tables =
                c.getMetaData().getTables(c.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                String table = tables.getString("TABLE_NAME");
                assertTrue(table.matches("[a-zA-Z0-9_]+"));
                List<String> names = new ArrayList<>();
                try (var statement = c.createStatement();
                        var rows =
                                statement.executeQuery("SELECT * FROM `" + table + "` WHERE 1=0")) {
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++)
                        names.add(rows.getMetaData().getColumnName(i));
                }
                result.put(table, names);
            }
        }
        return result;
    }

    private static void copyRows(
            Connection reader, Connection writer, String table, List<String> names)
            throws Exception {
        String fields =
                names.stream().map(name -> "`" + name + "`").collect(Collectors.joining(","));
        String markers = String.join(",", Collections.nCopies(names.size(), "?"));
        try (var statement = reader.createStatement();
                var rows = statement.executeQuery("SELECT " + fields + " FROM `" + table + "`");
                var insert =
                        writer.prepareStatement(
                                "INSERT INTO `"
                                        + table
                                        + "` ("
                                        + fields
                                        + ") VALUES("
                                        + markers
                                        + ")")) {
            while (rows.next()) {
                for (int i = 1; i <= names.size(); i++) insert.setObject(i, rows.getObject(i));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    static Map<String, String> fingerprints(Connection c, Map<String, List<String>> columns)
            throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (var table : columns.entrySet()) {
            if (table.getKey().equals("flyway_schema_history")) continue;
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            String fields =
                    table.getValue().stream()
                            .map(name -> "`" + name + "`")
                            .collect(Collectors.joining(","));
            try (var statement = c.createStatement();
                    var rows =
                            statement.executeQuery(
                                    "SELECT "
                                            + fields
                                            + " FROM `"
                                            + table.getKey()
                                            + "` ORDER BY id")) {
                while (rows.next())
                    for (int i = 1; i <= table.getValue().size(); i++) {
                        byte[] bytes =
                                Objects.toString(rows.getObject(i), "<NULL>")
                                        .getBytes(StandardCharsets.UTF_8);
                        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                        hash.update(bytes);
                    }
            }
            result.put(table.getKey(), HexFormat.of().formatHex(hash.digest()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> settings() throws Exception {
        try (var input = Files.newInputStream(Path.of("src/main/resources/application-dev.yml"))) {
            Map<String, Object> yaml = new Yaml().load(input);
            Map<String, Object> qivo = (Map<String, Object>) yaml.get("qivo");
            return new HashMap<>((Map<String, Object>) qivo.get("datasource"));
        }
    }
}
