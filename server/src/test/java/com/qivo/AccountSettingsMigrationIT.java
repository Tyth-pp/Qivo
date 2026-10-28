package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** 显式运行：源库仅 SELECT，V8 只应用于随机账号表副本。 */
class AccountSettingsMigrationIT {
    @Test void v8PreservesExistingAccountsAndIsRepeatable() throws Exception {
        assertEquals("qivo", System.getProperty("qivo.account.source"));
        var config = SkuMigrationIT.settings();
        assertEquals("qivo", config.get("database"));
        String copy = "qivo_account_copy_" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
        assertTrue(copy.matches("qivo_account_copy_[a-f0-9]{12}"));
        String base = "jdbc:mysql://"+config.get("host")+":"+config.get("port")+"/";
        String options = "?characterEncoding=utf8&serverTimezone=Asia/Shanghai&connectTimeout=4000&socketTimeout=10000&sslMode=REQUIRED";
        String user = config.get("username").toString(), password = config.get("password").toString();
        try (var db = DriverManager.getConnection(base+"qivo"+options,user,password); var sql = db.createStatement()) {
            sql.execute("CREATE DATABASE "+copy);
            try {
                sql.execute("CREATE TABLE "+copy+".admin LIKE qivo.admin");
                sql.execute("INSERT INTO "+copy+".admin SELECT * FROM qivo.admin");
                List<String> before = new ArrayList<>();
                try (var rows = sql.executeQuery("SELECT id,username,password,status FROM "+copy+".admin ORDER BY id")) {
                    while(rows.next()) before.add(rows.getLong(1)+"|"+rows.getString(2)+"|"+rows.getString(3)+"|"+rows.getInt(4));
                }
                try (var rows=sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"+copy+"' AND table_name='admin' AND column_name='avatar'")) {
                    rows.next(); if(rows.getInt(1)>0) sql.execute("ALTER TABLE "+copy+".admin DROP COLUMN avatar");
                }
                var flyway=Flyway.configure().dataSource(base+copy+options,user,password).baselineOnMigrate(true).baselineVersion("7").load();
                assertEquals(1,flyway.migrate().migrationsExecuted);
                assertEquals(0,flyway.migrate().migrationsExecuted);
                List<String> after = new ArrayList<>();
                try(var rows=sql.executeQuery("SELECT id,username,password,status,avatar FROM "+copy+".admin ORDER BY id")) {
                    while(rows.next()) { after.add(rows.getLong(1)+"|"+rows.getString(2)+"|"+rows.getString(3)+"|"+rows.getInt(4));assertNull(rows.getString(5)); }
                }
                assertTrue(before.equals(after),"迁移不得改变已有账号、密码或状态");
            } finally { sql.execute("DROP DATABASE "+copy); }
        }
    }
}
