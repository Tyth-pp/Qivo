package com.qivo.development;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;

/** 仅在明确选择时加入首次初始化事务，不用于已有数据库补订单。 */
final class ProjectTransactionSamples {
    private ProjectTransactionSamples() {}

    static void insert(Connection connection) throws Exception {
        long productId, skuId;
        String name, code;
        BigDecimal price;
        int stock;
        try (Statement sql = connection.createStatement(); var rows = sql.executeQuery(
                "SELECT p.id,p.name,s.id,s.sku_code,s.price,s.stock FROM product p"
                        + " JOIN product_sku s ON s.product_id=p.id WHERE p.spec_mode='SINGLE'"
                        + " AND p.status=1 AND s.status='ENABLED' AND s.stock>=2 ORDER BY p.id,s.id LIMIT 1")) {
            if (!rows.next()) throw new IllegalStateException("没有可用于交易样例的在售单规格商品");
            productId = rows.getLong(1); name = rows.getString(2); skuId = rows.getLong(3);
            code = rows.getString(4); price = rows.getBigDecimal(5); stock = rows.getInt(6);
        }
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO address(id,user_id,receiver_name,receiver_phone,province,city,district,detail,is_default)"
                    + " VALUES (1,1,'用户甲','13800000001','浙江省','杭州市','西湖区','示例路1号（虚构地址）',1),"
                    + "(2,2,'用户乙','13800000002','浙江省','杭州市','西湖区','示例路2号（虚构地址）',1)");
        }
        for (int id = 1; id <= 2; id++) {
            try (var sql = connection.prepareStatement(
                    "INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,address_id,pay_time,ship_time,"
                            + "receiver_name,receiver_phone,receiver_address_snapshot,address_snapshot_source)"
                            + " SELECT ?,?,?,?,1,?,?,NOW(),IF(?=2,NOW(),NULL),receiver_name,receiver_phone,"
                            + "CONCAT_WS(' ',province,city,district,detail),'CURRENT' FROM address WHERE id=?")) {
                sql.setInt(1, id); sql.setString(2, "QIVO-SAMPLE-" + id); sql.setInt(3, id);
                sql.setBigDecimal(4, price); sql.setInt(5, id == 1 ? 0 : 2);
                sql.setInt(6, id); sql.setInt(7, id); sql.setInt(8, id);
                sql.executeUpdate();
            }
            try (var sql = connection.prepareStatement(
                    "INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,total_price,"
                            + "sku_id,sku_code,sku_spec_snapshot,sku_snapshot_source)"
                            + " VALUES (?,?,?,1,?,?,?,?,JSON_OBJECT('text','标准款','selections',JSON_ARRAY()),'CURRENT')")) {
                sql.setInt(1, id); sql.setLong(2, productId); sql.setString(3, name);
                sql.setBigDecimal(4, price); sql.setBigDecimal(5, price);
                sql.setLong(6, skuId); sql.setString(7, code); sql.executeUpdate();
            }
            try (var sql = connection.prepareStatement(
                    "INSERT INTO stock_movement(product_id,sku_id,order_id,event_key,kind,delta,stock_before,stock_after,actor_id,actor_domain)"
                            + " VALUES (?,?,?,?,'ORDER_RESERVE',-1,?,?,?,'USER')")) {
                sql.setLong(1, productId); sql.setLong(2, skuId); sql.setInt(3, id);
                sql.setString(4, "project-sample-reserve:" + id); sql.setInt(5, stock - id + 1);
                sql.setInt(6, stock - id); sql.setInt(7, id); sql.executeUpdate();
            }
        }
        try (var sql = connection.prepareStatement("UPDATE product_sku SET stock=stock-2 WHERE id=? AND stock>=2")) {
            sql.setLong(1, skuId);
            if (sql.executeUpdate() != 1) throw new IllegalStateException("交易样例库存不足");
        }
        try (var sql = connection.prepareStatement(
                "UPDATE product SET sales=sales+2,stock=(SELECT SUM(stock) FROM product_sku WHERE product_id=?) WHERE id=?")) {
            sql.setLong(1, productId); sql.setLong(2, productId); sql.executeUpdate();
        }
    }
}
