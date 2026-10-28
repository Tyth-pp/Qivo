package com.qivo.migration;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** SKU 演练前的只读校验；不修改数据，不自动修复异常历史记录。 */
public final class SkuMigrationPreflight {
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");

    private SkuMigrationPreflight() {}

    public static void validateServer(Connection connection) throws Exception {
        String version = connection.getMetaData().getDatabaseProductVersion();
        var matcher = VERSION.matcher(version);
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())
                || version.toLowerCase(Locale.ROOT).contains("mariadb")
                || !matcher.find()) throw new IllegalStateException("SKU演练仅支持MySQL 8.0.16及以上版本");
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        if (major < 8 || (major == 8 && minor == 0 && patch < 16))
            throw new IllegalStateException("SKU演练需要MySQL 8.0.16及以上版本，以实际执行CHECK约束");
    }

    public static void validate(Connection connection) throws Exception {
        validateServer(connection);
        try (var tables =
                connection
                        .getMetaData()
                        .getTables(
                                connection.getCatalog(),
                                null,
                                "product_sku",
                                new String[] {"TABLE"})) {
            if (tables.next()) throw new IllegalStateException("副本已存在SKU表，请使用新的未升级副本演练");
        }
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put(
                "商品价格、原价、库存或状态非法",
                "SELECT COUNT(*) FROM product WHERE price IS NULL OR price<=0 OR stock IS NULL OR"
                    + " stock<0 OR status NOT IN(0,1) OR (original_price IS NOT NULL AND"
                    + " original_price<price)");
        checks.put(
                "商品分类引用缺失",
                "SELECT COUNT(*) FROM product p LEFT JOIN product_category c ON c.id=p.category_id"
                    + " WHERE c.id IS NULL");
        checks.put(
                "购物车商品或用户引用缺失",
                "SELECT COUNT(*) FROM cart c LEFT JOIN product p ON p.id=c.product_id LEFT JOIN"
                    + " `user` u ON u.id=c.user_id WHERE p.id IS NULL OR u.id IS NULL");
        checks.put("购物车数量非法", "SELECT COUNT(*) FROM cart WHERE quantity IS NULL OR quantity<=0");
        checks.put(
                "订单用户引用缺失",
                "SELECT COUNT(*) FROM `order` o LEFT JOIN `user` u ON u.id=o.user_id WHERE u.id IS"
                    + " NULL");
        checks.put(
                "订单明细商品或订单引用缺失",
                "SELECT COUNT(*) FROM order_item i LEFT JOIN product p ON p.id=i.product_id LEFT"
                    + " JOIN `order` o ON o.id=i.order_id WHERE p.id IS NULL OR o.id IS NULL");
        checks.put(
                "订单明细数量或金额非法",
                "SELECT COUNT(*) FROM order_item WHERE quantity IS NULL OR quantity<=0 OR"
                    + " unit_price<=0 OR total_price<>unit_price*quantity");
        for (var check : checks.entrySet()) {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery(check.getValue())) {
                rows.next();
                long count = rows.getLong(1);
                if (count != 0)
                    throw new IllegalStateException(
                            "SKU迁移前校验失败：" + check.getKey() + "，记录数=" + count);
            }
        }
    }
}
