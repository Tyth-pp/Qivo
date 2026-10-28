package com.qivo;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.cache.RedisStore;
import com.qivo.config.MyBatisConfig;
import com.qivo.development.DevelopmentDataInitializer;
import com.qivo.entity.Product;
import com.qivo.entity.ProductSku;
import com.qivo.enums.SkuStatus;
import com.qivo.mapper.ProductMapper;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.migration.LegacySchemaBaseline;
import com.qivo.persistence.PageRequests;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.Passwords;
import com.qivo.security.SessionRegistry;
import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/** 显式运行的真实 MySQL/Redis 验收：只读源库，在随机副本/空库执行写入。 */
class FoundationInfrastructureIT {
    @Test
    @SuppressWarnings("unchecked")
    void snapshotBaselineFreshMigrationCrudPaginationAndRealRedisSessions() throws Exception {
        assertEquals(
                "true", System.getProperty("qivo.integration"), "必须显式指定 -Dqivo.integration=true");
        Map<String, Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application-dev.yml"))) {
            config = new Yaml().load(input);
        }
        var qivo = (Map<String, Object>) config.get("qivo");
        Map<String, Object> redisSettings = new HashMap<>((Map<String, Object>) qivo.get("redis"));
        String redisOverride = System.getProperty("qivo.integration.redis.host");
        if (redisOverride != null) {
            redisSettings.put("host", redisOverride);
            redisSettings.put(
                    "port",
                    Integer.parseInt(System.getProperty("qivo.integration.redis.port", "6379")));
            redisSettings.put(
                    "password",
                    Objects.toString(System.getenv("QIVO_INTEGRATION_REDIS_PASSWORD"), ""));
        }
        var db = (Map<String, Object>) qivo.get("datasource");
        String source = System.getProperty("qivo.integration.legacy-source");
        assertNotNull(source, "必须显式指定历史只读源库，如 -Dqivo.integration.legacy-source=mall");
        assertTrue(source.matches("[a-zA-Z0-9_]+"));
        String url = "jdbc:mysql://" + db.get("host") + ":" + db.get("port") + "/";
        // 隔离库验收要求 TLS，避免依赖 MySQL 已缓存的 SHA-2 快速认证。
        String options =
                "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user = db.get("username").toString(), password = db.get("password").toString();
        String
                copy =
                        "qivo_stage2_"
                                + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                fresh = copy + "_fresh";
        List<String> tables = new ArrayList<>();
        try (Connection admin =
                DriverManager.getConnection(url + source + options, user, password)) {
            try (var rows =
                    admin.getMetaData().getTables(source, null, "%", new String[] {"TABLE"})) {
                while (rows.next()) {
                    String table = rows.getString("TABLE_NAME");
                    assertTrue(table.matches("[a-zA-Z0-9_]+"));
                    tables.add(table);
                }
            }
            assertEquals(16, tables.size(), "源库应为历史16张表；结构变化需重新审查后再测试");
            try (var stmt = admin.createStatement()) {
                stmt.execute(
                        "CREATE DATABASE `"
                                + copy
                                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                stmt.execute(
                        "CREATE DATABASE `"
                                + fresh
                                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            try {
                // 源数据只读；测试时应避免其他开发实例并发写入，以确保快照一致。
                admin.setAutoCommit(false);
                admin.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try (var stmt = admin.createStatement()) {
                    for (String table : tables)
                        stmt.execute(
                                "CREATE TABLE `"
                                        + copy
                                        + "`.`"
                                        + table
                                        + "` LIKE `"
                                        + source
                                        + "`.`"
                                        + table
                                        + "`");
                }
                admin.commit();
                try (var stmt = admin.createStatement()) {
                    for (String table : tables)
                        stmt.execute(
                                "INSERT INTO `"
                                        + copy
                                        + "`.`"
                                        + table
                                        + "` SELECT * FROM `"
                                        + source
                                        + "`.`"
                                        + table
                                        + "`");
                }
                admin.commit();
                admin.setAutoCommit(true);
                try (HikariDataSource ds = new HikariDataSource()) {
                    ds.setJdbcUrl(url + copy + options);
                    ds.setUsername(user);
                    ds.setPassword(password);
                    ds.setMaximumPoolSize(2);
                    ds.setMinimumIdle(0);
                    Map<String, String> before;
                    try (Connection c = ds.getConnection()) {
                        LegacySchemaBaseline.validate(c);
                        before = fingerprints(c, tables);
                        try (var stmt = c.createStatement()) {
                            stmt.execute("ALTER TABLE product DROP INDEX idx_status");
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> LegacySchemaBaseline.validate(c));
                            stmt.execute("ALTER TABLE product ADD INDEX idx_status(status)");
                        }
                        LegacySchemaBaseline.validate(c);
                    }
                    Flyway flyway =
                            Flyway.configure()
                                    .target("3")
                                    .dataSource(ds)
                                    .baselineOnMigrate(false)
                                    .cleanDisabled(true)
                                    .load();
                    assertThrows(FlywayException.class, flyway::migrate, "非空旧库不能自动升级");
                    flyway.baseline();
                    assertEquals(2, flyway.migrate().migrationsExecuted);
                    assertEquals(0, flyway.migrate().migrationsExecuted);
                    try (Connection c = ds.getConnection()) {
                        assertEquals(before, fingerprints(c, tables), "迁移应保持全部业务行数据不变");
                        try (var rows =
                                c.getMetaData().getColumns(copy, null, "user", "password")) {
                            assertTrue(rows.next());
                            assertEquals(255, rows.getInt("COLUMN_SIZE"));
                        }
                    }
                }
                Flyway freshFlyway =
                        Flyway.configure()
                                .dataSource(url + fresh + options, user, password)
                                .cleanDisabled(true)
                                .load();
                assertEquals(7, freshFlyway.migrate().migrationsExecuted);
                assertEquals(0, freshFlyway.migrate().migrationsExecuted);
                try (Connection c =
                                DriverManager.getConnection(url + fresh + options, user, password);
                        var stmt = c.createStatement()) {
                    assertEquals(0, count(stmt, "product"));
                    assertEquals(0, count(stmt, "admin"));
                    assertEquals(0, count(stmt, "stock_movement"));
                    assertEquals(0, count(stmt, "order_submission"));
                    Map<String, String> seedPasswords =
                            Map.of(
                                    "admin",
                                    "devAdmin123",
                                    "product_admin",
                                    "devOperator123",
                                    "buyer",
                                    "devBuyer123",
                                    "buyer_other",
                                    "devBuyer123");
                    DevelopmentDataInitializer.initialize(c, fresh, seedPasswords);
                    assertEquals(2, count(stmt, "admin"));
                    assertEquals(2, count(stmt, "user"));
                    assertEquals(5, count(stmt, "product"));
                    assertThrows(
                            IllegalStateException.class,
                            () -> DevelopmentDataInitializer.initialize(c, fresh, seedPasswords));
                    assertEquals(2, count(stmt, "admin"));
                    try (var accounts =
                            stmt.executeQuery(
                                    "SELECT password FROM admin WHERE username='admin'")) {
                        assertTrue(accounts.next());
                        assertTrue(new Passwords().matches("devAdmin123", accounts.getString(1)));
                    }
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> DevelopmentDataInitializer.initialize(c, "mall", seedPasswords));
                }
                try (var modern = new HikariDataSource()) {
                    modern.setJdbcUrl(url + fresh + options);
                    modern.setUsername(user);
                    modern.setPassword(password);
                    modern.setMinimumIdle(0);
                    verifyGeneratedCrud(modern);
                }
                verifyHttpServer(url + fresh + options, user, password, redisSettings);
                verifyRedis(redisSettings);
            } finally {
                assertTrue(copy.matches("qivo_stage2_[a-f0-9]{12}"));
                assertEquals(copy + "_fresh", fresh);
                try (var stmt = admin.createStatement()) {
                    stmt.execute("DROP DATABASE IF EXISTS `" + copy + "`");
                    stmt.execute("DROP DATABASE IF EXISTS `" + fresh + "`");
                }
            }
        }
    }

    // Legacy mall is verified only against its V3 baseline. Current SKU CRUD/HTTP run on a fresh
    // schema.
    private static void verifyGeneratedCrud(HikariDataSource ds) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        factory.setPlugins(new MyBatisConfig().mybatisPlusInterceptor());
        var cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(cfg);
        try (var session = Objects.requireNonNull(factory.getObject()).openSession(false)) {
            ProductMapper products = session.getMapper(ProductMapper.class);
            for (int i = 0; i < 3; i++) {
                Product p = new Product();
                p.setName("阶段二真实库验收" + i);
                p.setPrice(new BigDecimal("12.50"));
                p.setStock(3);
                p.setStatus(1);
                p.setCategoryId(1L);
                assertEquals(1, products.insert(p));
                assertNotNull(p.getId());
                assertEquals(3, products.selectById(p.getId()).getStock());
                var sku = new ProductSku();
                sku.setProductId(p.getId());
                sku.setSkuCode("INFRA-" + p.getId());
                sku.setSpecKey("DEFAULT");
                sku.setSpecValues("[]");
                sku.setPrice(p.getPrice());
                sku.setStock(3);
                sku.setStatus(SkuStatus.ENABLED);
                session.getMapper(ProductSkuMapper.class).insert(sku);
            }
            var page = PageRequests.<Product>of(1, 2);
            assertEquals(2, products.selectPage(page, "阶段二真实库验收", null, 1, null).size());
            assertEquals(3, page.getTotal());
            session.rollback();
        }
    }

    private static long count(Statement statement, String table) throws Exception {
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static void verifyHttpServer(
            String url, String username, String password, Map<String, Object> redis)
            throws Exception {
        String secret = "qivo-http-test-only-secret-never-use-production-2026";
        var app =
                new SpringApplicationBuilder(QivoApplication.class)
                        .run(
                                "--spring.profiles.active=test",
                                "--server.port=0",
                                "--spring.main.banner-mode=off",
                                "--spring.datasource.url=" + url,
                                "--spring.datasource.username=" + username,
                                "--spring.datasource.password=" + password,
                                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                                "--spring.sql.init.mode=never",
                                "--spring.flyway.enabled=true",
                                "--springdoc.api-docs.enabled=true",
                                "--springdoc.swagger-ui.enabled=true",
                                "--jwt.secret=" + secret,
                                "--spring.data.redis.host=" + redis.get("host"),
                                "--spring.data.redis.port=" + redis.get("port"),
                                "--spring.data.redis.password="
                                        + Objects.toString(redis.get("password"), ""),
                                "--logging.level.com.qivo=WARN");
        long id =
                Long.parseLong(UUID.randomUUID().toString().replace("-", "").substring(0, 12), 16);
        var json = app.getBean(ObjectMapper.class);
        var jdbc = app.getBean(JdbcTemplate.class);
        var redisStore = app.getBean(RedisStore.class);
        var strings = app.getBean(StringRedisTemplate.class);
        List<String> tokens = new ArrayList<>();
        try {
            String buyer = "buyer_" + id, operator = "admin_" + id;
            String initialHash = new Passwords().encode("password123");
            jdbc.update(
                    "INSERT INTO `user`(id,username,password,status,score) VALUES(?,?,?,1,0)",
                    id,
                    buyer,
                    initialHash);
            jdbc.update(
                    "INSERT INTO admin(id,username,password,status) VALUES(?,?,?,1)",
                    id,
                    operator,
                    initialHash);
            Long superRole =
                    jdbc.queryForObject("SELECT id FROM role WHERE code='super_admin'", Long.class);
            jdbc.update("INSERT INTO admin_role(admin_id,role_id) VALUES(?,?)", id, superRole);
            int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
            var client = HttpClient.newHttpClient();
            class Http {
                HttpResponse<String> request(String method, String path, String token, Object body)
                        throws Exception {
                    var builder =
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                    .timeout(Duration.ofSeconds(15));
                    if (token != null) builder.header("Authorization", "Bearer " + token);
                    if (body != null) builder.header("Content-Type", "application/json");
                    builder.method(
                            method,
                            body == null
                                    ? HttpRequest.BodyPublishers.noBody()
                                    : HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body)));
                    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                }

                String login(String loginPath, String name) throws Exception {
                    return login(loginPath, name, "password123");
                }

                String login(String loginPath, String name, String rawPassword) throws Exception {
                    var captcha = request("GET", "/api/common/captcha/get", null, null);
                    assertEquals(200, captcha.statusCode());
                    var data = json.readTree(captcha.body());
                    assertTrue(
                            data.get("captchaImage").asText().startsWith("data:image/png;base64,"));
                    String key = data.get("captchaKey").asText();
                    String code = (String) redisStore.get("captcha:" + key);
                    var response =
                            request(
                                    "POST",
                                    loginPath,
                                    null,
                                    Map.of(
                                            "username",
                                            name,
                                            "password",
                                            rawPassword,
                                            "captchaKey",
                                            key,
                                            "captchaCode",
                                            code));
                    assertEquals(200, response.statusCode(), "真实登录失败");
                    String token =
                            json.readTree(response.body()).path("data").path("token").asText();
                    assertFalse(token.isBlank());
                    tokens.add(token);
                    assertNull(redisStore.get("captcha:" + key), "验证码只能消费一次");
                    return token;
                }
            }
            Http http = new Http();
            assertEquals(401, http.request("GET", "/api/fore/cart/list", null, null).statusCode());
            String userToken = http.login("/api/common/auth/login", buyer);
            String adminToken = http.login("/api/common/auth/adminLogin", operator);
            assertEquals(
                    401, http.request("POST", "/api/common/upload/file", null, Map.of()).statusCode());
            assertEquals(
                    403,
                    http.request("POST", "/api/common/upload/file", userToken, Map.of()).statusCode());
            assertTrue(
                    jdbc.queryForObject("SELECT password FROM `user` WHERE id=?", String.class, id)
                            .startsWith("$2"));
            var profile = http.request("GET", "/api/fore/user/profile", userToken, null);
            assertEquals(200, profile.statusCode());
            assertFalse(json.readTree(profile.body()).path("data").has("password"));
            assertTrue(profile.headers().firstValue("X-Request-ID").isPresent());
            assertEquals(
                    403, http.request("GET", "/api/admin/profile", userToken, null).statusCode());
            assertEquals(
                    403,
                    http.request("GET", "/api/fore/user/profile", adminToken, null).statusCode());
            assertEquals(
                    200,
                    http.request(
                                    "GET",
                                    "/api/admin/product/list?page=1&pageSize=2",
                                    adminToken,
                                    null)
                            .statusCode());
            assertEquals(200, http.request("GET", "/v3/api-docs", null, null).statusCode());
            assertEquals(
                    400,
                    http.request("GET", "/api/admin/product/list?pageSize=101", adminToken, null)
                            .statusCode());
            String limited = "limited_" + id;
            assertEquals(
                    200,
                    http.request(
                                    "POST",
                                    "/api/admin/admin/add",
                                    adminToken,
                                    Map.of("username", limited, "password", "password123"))
                            .statusCode());
            Long limitedId =
                    jdbc.queryForObject(
                            "SELECT id FROM admin WHERE username=?", Long.class, limited);
            Long productRole =
                    jdbc.queryForObject(
                            "SELECT id FROM role WHERE code='product_admin'", Long.class);
            assertEquals(
                    200,
                    http.request(
                                    "PUT",
                                    "/api/admin/admin/role",
                                    adminToken,
                                    Map.of("adminId", limitedId, "roleId", productRole))
                            .statusCode());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM admin_role WHERE admin_id=?",
                            Integer.class,
                            limitedId));
            String limitedToken = http.login("/api/common/auth/adminLogin", limited, "password123");
            var limitedAccessResponse = http.request("GET", "/api/admin/access", limitedToken, null);
            assertEquals(200, limitedAccessResponse.statusCode());
            var limitedAccess = json.readTree(limitedAccessResponse.body()).path("data");
            assertEquals(limitedId.longValue(), limitedAccess.path("adminId").asLong());
            assertTrue(limitedAccess.path("capabilities").path("product:list").asBoolean());
            assertFalse(limitedAccess.path("capabilities").path("order:list").asBoolean());
            assertFalse(limitedAccess.path("capabilities").path("role:write").asBoolean());
            assertEquals(
                    403, http.request("GET", "/api/admin/user/list", limitedToken, null).statusCode());
            assertEquals(
                    403, http.request("GET", "/api/admin/role/list", limitedToken, null).statusCode());
            assertEquals(
                    200,
                    http.request("GET", "/api/admin/product/list", limitedToken, null)
                            .statusCode());
            assertEquals(
                    403,
                    http.request("GET", "/api/admin/order/list", limitedToken, null).statusCode());
            jdbc.update("DELETE FROM admin_role WHERE admin_id=?", limitedId);
            var revokedAccess = json.readTree(http.request("GET", "/api/admin/access", limitedToken, null).body()).path("data");
            assertFalse(revokedAccess.path("capabilities").path("product:list").asBoolean());
            assertEquals(403, http.request("GET", "/api/admin/product/list", limitedToken, null).statusCode());
            assertEquals(200,http.request("PUT","/api/admin/admin/"+limitedId+"/status",adminToken,Map.of("status",0)).statusCode());
            assertEquals(401,http.request("GET","/api/admin/profile",limitedToken,null).statusCode());
            assertEquals(200,http.request("PUT","/api/admin/admin/"+limitedId+"/status",adminToken,Map.of("status",1)).statusCode());
            assertEquals(401,http.request("GET","/api/admin/profile",limitedToken,null).statusCode(),"重新启用不能复活旧管理员会话");
            String renewedLimitedToken=http.login("/api/common/auth/adminLogin",limited,"password123");
            assertEquals(200,http.request("GET","/api/admin/profile",renewedLimitedToken,null).statusCode());
            assertEquals(200,http.request("PUT","/api/admin/admin/"+limitedId+"/password",adminToken,Map.of("password","resetPassword123")).statusCode());
            assertEquals(401,http.request("GET","/api/admin/profile",renewedLimitedToken,null).statusCode(),"重设密码必须使原会话失效");
            http.login("/api/common/auth/adminLogin",limited,"resetPassword123");
            var accountList=http.request("GET","/api/admin/admin/list?page=1&pageSize=10",adminToken,null);
            assertEquals(200,accountList.statusCode());
            for(var account:json.readTree(accountList.body()).path("data").path("records"))assertFalse(account.has("password"));
            String passwordBefore =
                    jdbc.queryForObject("SELECT password FROM `user` WHERE id=?", String.class, id);
            assertEquals(
                    200,
                    http.request(
                                    "PUT",
                                    "/api/fore/user/profile",
                                    userToken,
                                    Map.of(
                                            "email",
                                            "test@example.com",
                                            "password",
                                            "injected",
                                            "status",
                                            0))
                            .statusCode());
            assertEquals(
                    passwordBefore,
                    jdbc.queryForObject(
                            "SELECT password FROM `user` WHERE id=?", String.class, id));
            assertEquals(
                    1,
                    jdbc.queryForObject("SELECT status FROM `user` WHERE id=?", Integer.class, id));
            jdbc.update(
                    "INSERT INTO cart(user_id,product_id,sku_id,quantity,checked) SELECT ?,1,id,1,1"
                            + " FROM product_sku WHERE product_id=1",
                    id + 1);
            Long cartId =
                    jdbc.queryForObject("SELECT id FROM cart WHERE user_id=?", Long.class, id + 1);
            assertEquals(
                    404,
                    http.request(
                                    "PUT",
                                    "/api/fore/cart/update",
                                    userToken,
                                    Map.of("cartId", cartId, "quantity", 2))
                            .statusCode());
            assertEquals(
                    404,
                    http.request("DELETE", "/api/fore/cart/delete/" + cartId, userToken, null)
                            .statusCode());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT quantity FROM cart WHERE id=?", Integer.class, cartId));
            jdbc.update(
                    "INSERT INTO"
                        + " address(user_id,receiver_name,receiver_phone,province,city,district,detail,is_default)"
                        + " VALUES(?,'测试','10000000000','测试','测试','测试','测试',0)",
                    id + 1);
            Long addressId =
                    jdbc.queryForObject(
                            "SELECT id FROM address WHERE user_id=?", Long.class, id + 1);
            assertEquals(
                    404,
                    http.request(
                                    "PUT",
                                    "/api/fore/user/address/" + addressId,
                                    userToken,
                                    Map.of("isDefault", 1, "detail", "injected"))
                            .statusCode());
            assertEquals(
                    404,
                    http.request("DELETE", "/api/fore/user/address/" + addressId, userToken, null)
                            .statusCode());
            jdbc.update(
                    "INSERT INTO"
                        + " `order`(order_no,user_id,total_amount,pay_status,ship_status,address_id)"
                        + " VALUES(?,?,1,0,0,?)",
                    "stage2_" + id,
                    id + 1,
                    addressId);
            Long orderId =
                    jdbc.queryForObject(
                            "SELECT id FROM `order` WHERE order_no=?", Long.class, "stage2_" + id);
            Long snapshotSku = jdbc.queryForObject(
                    "SELECT id FROM product_sku WHERE product_id=1 ORDER BY id LIMIT 1", Long.class);
            String snapshotSkuCode = jdbc.queryForObject(
                    "SELECT sku_code FROM product_sku WHERE id=?", String.class, snapshotSku);
            jdbc.update(
                    "INSERT INTO order_item(order_id,product_id,sku_id,sku_code,product_name,product_img,quantity,"
                            + "unit_price,total_price,sku_spec_snapshot,sku_snapshot_source)"
                            + " VALUES(?,1,?,?,?,?,2,50,100,?,'CURRENT')",
                    orderId,
                    snapshotSku,
                    snapshotSkuCode,
                    "购买时名称，与当前商品不同",
                    "/upload/order-snapshot.png",
                    "{\"text\":\"暖白 / 480ml\"}");
            jdbc.update(
                    "INSERT INTO order_item(order_id,product_id,sku_id,sku_code,product_name,quantity,unit_price,"
                            + "total_price,sku_snapshot_source) VALUES(?,1,?,?,'历史商品',1,10,10,'LEGACY')",
                    orderId, snapshotSku, snapshotSkuCode);
            var adminDetail =
                    http.request("GET", "/api/admin/order/detail/" + orderId, adminToken, null);
            assertEquals(200, adminDetail.statusCode());
            var stored = json.readTree(adminDetail.body()).path("data");
            assertEquals("stage2_" + id, stored.path("orderNo").asText());
            assertEquals(2, stored.path("items").size());
            assertEquals(
                    "购买时名称，与当前商品不同",
                    stored.path("items").get(0).path("productName").asText());
            assertEquals(
                    "/upload/order-snapshot.png",
                    stored.path("items").get(0).path("productImg").asText());
            assertEquals(
                    "暖白 / 480ml",
                    json.readTree(stored.path("items").get(0).path("skuSpecSnapshot").asText())
                            .path("text").asText());
            assertEquals(0, new BigDecimal("100.00").compareTo(
                    stored.path("items").get(0).path("totalPrice").decimalValue()));
            assertEquals(0, new BigDecimal("1.00").compareTo(stored.path("totalAmount").decimalValue()));
            assertTrue(stored.path("items").get(1).path("skuSpecSnapshot").isNull());
            assertTrue(stored.path("receiverAddressSnapshot").isNull());
            assertEquals(
                    403,
                    http.request("GET", "/api/admin/order/detail/" + orderId, userToken, null)
                            .statusCode());
            assertEquals(
                    404,
                    http.request("GET", "/api/fore/order/detail/" + orderId, userToken, null)
                            .statusCode());
            assertEquals(
                    409,
                    http.request(
                                    "POST",
                                    "/api/fore/user/resetPassword",
                                    userToken,
                                    Map.of("username", buyer, "newPassword", "injected"))
                            .statusCode());
            assertEquals(
                    409,
                    http.request("POST", "/api/fore/order/refund", userToken, Map.of("orderId", 1))
                            .statusCode());
            assertEquals(
                    200,
                    http.request(
                                    "PUT",
                                    "/api/admin/user/status/" + id,
                                    adminToken,
                                    Map.of("status", 0))
                            .statusCode());
            assertEquals(
                    401,
                    http.request("GET", "/api/fore/user/profile", userToken, null).statusCode());
            assertEquals(
                    200,
                    http.request(
                                    "PUT",
                                    "/api/admin/user/status/" + id,
                                    adminToken,
                                    Map.of("status", 1))
                            .statusCode());
            assertEquals(
                    401,
                    http.request("GET", "/api/fore/user/profile", userToken, null).statusCode(),
                    "重新启用不能复活旧令牌");
            userToken = http.login("/api/common/auth/login", buyer);
            assertEquals(
                    200,
                    http.request("POST", "/api/common/auth/logout", userToken, null).statusCode());
            assertEquals(
                    401,
                    http.request("GET", "/api/fore/user/profile", userToken, null).statusCode());
            assertEquals(
                    200,
                    http.request(
                                    "PUT",
                                    "/api/admin/password",
                                    adminToken,
                                    Map.of(
                                            "oldPassword",
                                            "password123",
                                            "newPassword",
                                            "changedPassword123"))
                            .statusCode());
            assertEquals(
                    401,
                    http.request("GET", "/api/admin/profile", adminToken, null).statusCode(),
                    "改密后原管理员令牌必须失效");
            String changedAdminToken =
                    http.login("/api/common/auth/adminLogin", operator, "changedPassword123");
            assertEquals(
                    200,
                    http.request("POST", "/api/common/auth/logout", changedAdminToken, null)
                            .statusCode());
        } finally {
            try {
                for (String token : tokens) {
                    var claims =
                            json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
                    strings.delete("qivo:session:v2:" + claims.get("jti").asText());
                }
                if (!tokens.isEmpty()) strings.delete("qivo:session-version:v2:USER:" + id);
            } finally {
                app.close();
            }
        }
    }

    private static Map<String, String> fingerprints(Connection connection, List<String> tables)
            throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (String table : tables) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (var stmt = connection.createStatement();
                    var rows = stmt.executeQuery("SELECT * FROM `" + table + "` ORDER BY id")) {
                while (rows.next())
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        byte[] value =
                                Objects.toString(rows.getObject(i), "<NULL>")
                                        .getBytes(StandardCharsets.UTF_8);
                        hash.update(ByteBuffer.allocate(4).putInt(value.length).array());
                        hash.update(value);
                    }
            }
            result.put(table, HexFormat.of().formatHex(hash.digest()));
        }
        return result;
    }

    private static void verifyRedis(Map<String, Object> settings) throws Exception {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(
                        settings.get("host").toString(),
                        Integer.parseInt(settings.get("port").toString()));
        if (settings.get("password") != null && !settings.get("password").toString().isBlank())
            config.setPassword(RedisPassword.of(settings.get("password").toString()));
        LettuceConnectionFactory connection = new LettuceConnectionFactory(config);
        connection.afterPropertiesSet();
        connection.start();
        StringRedisTemplate template = new StringRedisTemplate(connection);
        SessionRegistry sessions = new SessionRegistry(template);
        String subject = "TEST:" + UUID.randomUUID(), id = UUID.randomUUID().toString();
        JwtTokenProvider jwt =
                new JwtTokenProvider(
                        "qivo-integration-test-secret-only-never-production-2026", 60000, sessions);
        List<String> sessionIds = new ArrayList<>();
        try {
            sessions.open(id, subject, 60000);
            assertTrue(sessions.active(id, subject));
            assertFalse(sessions.active(id, "ADMIN:1"));
            sessions.revoke(id);
            assertFalse(sessions.active(id, subject));
            Long account =
                    Long.parseLong(
                            UUID.randomUUID().toString().replace("-", "").substring(0, 12), 16);
            String token = jwt.generateToken(account, "isolated", "USER", "hash");
            var claims = jwt.parse(token);
            sessionIds.add(claims.getId());
            subject = claims.getSubject();
            sessions.revokeAll(subject);
            assertThrows(BadCredentialsException.class, () -> jwt.parse(token));
        } finally {
            try {
                sessions.revoke(id);
                for (String sid : sessionIds) sessions.revoke(sid);
                template.delete("qivo:session-version:v2:" + subject);
            } finally {
                connection.destroy();
            }
        }
    }
}
