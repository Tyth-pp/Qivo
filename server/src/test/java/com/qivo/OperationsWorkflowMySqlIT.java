package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** 继承同组业务断言，在随机空库验证真实MySQL锁与约束；只读本机配置，不连接业务表写入。 */
@SpringBootTest(properties={"spring.sql.init.mode=never","spring.flyway.enabled=false"})
@EnabledIfSystemProperty(named="qivo.operations.mysql",matches="true")
class OperationsWorkflowMySqlIT extends OperationsWorkflowTests {
    private static String database,base,options,user,password;
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        var settings=SkuMigrationIT.settings();assertEquals("qivo",settings.get("database"));
        database="qivo_operations_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        base="jdbc:mysql://"+settings.get("host")+":"+settings.get("port")+"/";
        options="?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        user=settings.get("username").toString();password=settings.get("password").toString();
        try(var connection=DriverManager.getConnection(base+"qivo"+options,user,password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE "+database+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try {Flyway.configure().dataSource(base+database+options,user,password).load().migrate();}
        catch(Exception e){cleanup();throw e;}
        registry.add("spring.datasource.url",()->base+database+options);
        registry.add("spring.datasource.username",()->user);registry.add("spring.datasource.password",()->password);
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode",()->"never");
    }
    @AfterAll static void cleanup() throws Exception {
        if(database==null)return;
        assertTrue(database.matches("qivo_operations_[a-f0-9]{12}"));
        try(var connection=DriverManager.getConnection(base+"qivo"+options,user,password);var sql=connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS "+database);
        }
    }
}
