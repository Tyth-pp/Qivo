package com.qivo.config;

import com.qivo.migration.SkuMigrationPreflight;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;

/** 在既有结构升级前执行只读检查；空库仍由 V1 开始初始化。 */
@Configuration
public class DatabaseMigrationConfig {
    @Bean
    FlywayMigrationStrategy validatedMigration() {
        return flyway -> {
            try (Connection connection =
                    flyway.getConfiguration().getDataSource().getConnection()) {
                if ("mall".equalsIgnoreCase(connection.getCatalog())
                        || "qivo_dev".equalsIgnoreCase(connection.getCatalog())) {
                    throw new IllegalStateException("历史库只用于备份，禁止自动迁移");
                }
                SkuMigrationPreflight.validateServer(connection);
                boolean product, sku;
                try (var tables =
                        connection
                                .getMetaData()
                                .getTables(
                                        connection.getCatalog(),
                                        null,
                                        "product",
                                        new String[] {"TABLE"})) {
                    product = tables.next();
                }
                try (var tables =
                        connection
                                .getMetaData()
                                .getTables(
                                        connection.getCatalog(),
                                        null,
                                        "product_sku",
                                        new String[] {"TABLE"})) {
                    sku = tables.next();
                }
                if (product && !sku) SkuMigrationPreflight.validate(connection);
            } catch (Exception exception) {
                throw new IllegalStateException("数据库迁移前检查未通过，未继续执行迁移", exception);
            }
            flyway.migrate();
        };
    }
}
