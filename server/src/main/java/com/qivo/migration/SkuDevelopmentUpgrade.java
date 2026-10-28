package com.qivo.migration;

import org.flywaydb.core.Flyway;

import java.sql.DriverManager;
import java.util.Set;

/** 维护窗口显式运行；执行前由操作者备份并在副本验收，凭据只通过环境变量传入。 */
public final class SkuDevelopmentUpgrade {
    private SkuDevelopmentUpgrade() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("请使用环境变量，不接受命令行凭据");
        String url = required("QIVO_DEV_DB_URL"),
                user = required("DB_USERNAME"),
                password = required("DB_PASSWORD");
        String target = required("QIVO_DEV_DATABASE_CONFIRM");
        if (!target.matches("[a-zA-Z0-9_]+")
                || Set.of(
                                "mall",
                                "qivo_dev",
                                "mysql",
                                "sys",
                                "information_schema",
                                "performance_schema")
                        .contains(target.toLowerCase()))
            throw new IllegalArgumentException("禁止升级历史或系统数据库");
        try (var c = DriverManager.getConnection(url, user, password)) {
            if (!target.equals(c.getCatalog()))
                throw new IllegalArgumentException("目标库名与明确确认的名称不一致");
            SkuMigrationPreflight.validate(c);
        }
        Flyway.configure()
                .dataSource(url, user, password)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load()
                .migrate();
        System.out.println("SKU 迁移完成，请核对回填数据和购买链路后恢复访问。");
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少环境变量：" + key);
        return value;
    }
}
