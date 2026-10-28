package com.qivo;
import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
/** 只接收外部明确恢复出的随机副本；不向日常库执行迁移。 */
class OperationsMigrationIT {
    @Test void actualBackupRowsSurviveV7AndMigrationIsRepeatable() throws Exception {
        String database=System.getProperty("qivo.operations.copy");
        assertNotNull(database);assertTrue(database.matches("qivo_ops_rehearsal_[a-f0-9]{12}"));
        var config=SkuMigrationIT.settings();
        String url="jdbc:mysql://"+config.get("host")+":"+config.get("port")+"/"+database+"?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user=config.get("username").toString(),password=config.get("password").toString();
        try(var connection=DriverManager.getConnection(url,user,password)) {
            var columns=SkuMigrationIT.columns(connection);var before=SkuMigrationIT.fingerprints(connection,columns);
            var flyway=Flyway.configure().dataSource(url,user,password).load();
            flyway.migrate();assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(before,SkuMigrationIT.fingerprints(connection,columns));
            try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM after_sale")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT COUNT(*) FROM refund_attempt")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
        }
    }
}
