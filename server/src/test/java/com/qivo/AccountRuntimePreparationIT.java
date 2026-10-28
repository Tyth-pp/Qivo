package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qivo.security.Passwords;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** 显式真实联调准备：仅写恢复出的随机副本，不接触日常账号。 */
class AccountRuntimePreparationIT {
    @Test void seedIsolatedRuntimeAccounts() throws Exception {
        var json = new ObjectMapper();
        Path manifest = Path.of("../.local/account-live-current.json");
        ObjectNode state = (ObjectNode) json.readTree(Files.readString(manifest));
        String database = state.path("database").asText();
        assertTrue(database.matches("qivo_account_live_[a-f0-9]{12}"));
        assertEquals(database,System.getProperty("qivo.account.copy"));
        var config = SkuMigrationIT.settings(); assertEquals("qivo",config.get("database"));
        String url = "jdbc:mysql://"+config.get("host")+":"+config.get("port")+"/"+database+"?characterEncoding=utf8&serverTimezone=Asia/Shanghai&sslMode=REQUIRED";
        String user=config.get("username").toString(),password=config.get("password").toString();
        Flyway.configure().dataSource(url,user,password).load().migrate();
        String raw="Qa_"+UUID.randomUUID().toString().replace("-","");
        String hash=new Passwords().encode(raw);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
        try(var db=DriverManager.getConnection(url,user,password)) {
            for(String type:new String[]{"user","admin"}) {
                String name="qa_account_"+suffix+"_"+type;
                String sql=type.equals("user")?"INSERT INTO user(username,password,status,score,create_time,update_time) VALUES(?,?,1,0,NOW(),NOW())":"INSERT INTO admin(username,password,status,real_name,create_time,update_time) VALUES(?,?,1,'隔离联调账号',NOW(),NOW())";
                try(var insert=db.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1,name);insert.setString(2,hash);insert.executeUpdate();
                    try(var keys=insert.getGeneratedKeys()){assertTrue(keys.next());state.put(type+"Id",keys.getLong(1));}
                }
                state.put(type+"Name",name);
            }
        }
        state.put("testPassword",raw);state.put("copyVersion","8");
        Files.writeString(manifest,json.writerWithDefaultPrettyPrinter().writeValueAsString(state));
    }
}
