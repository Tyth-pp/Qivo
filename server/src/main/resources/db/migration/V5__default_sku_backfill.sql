-- 仅在 SkuMigrationPreflight 通过后、写入停止的隔离副本执行。
-- 固定编码对应 SkuConstant，保留原商品状态、历史价格/库存和所有订单金额。
UPDATE product SET spec_definition = JSON_ARRAY(), update_time=update_time;
INSERT INTO product_sku(product_id,sku_code,spec_key,spec_values,price,original_price,stock,status,image)
SELECT id,CONCAT('QIVO-DEFAULT-',id),'DEFAULT',JSON_ARRAY(),price,original_price,stock,'ENABLED',main_image
FROM product ORDER BY id;

UPDATE cart c JOIN product_sku s ON s.product_id=c.product_id AND s.spec_key='DEFAULT'
SET c.sku_id=s.id, c.update_time=c.update_time;
UPDATE order_item i JOIN product_sku s ON s.product_id=i.product_id AND s.spec_key='DEFAULT'
SET i.sku_id=s.id, i.sku_code=s.sku_code;
-- 只补充库存定位信息；不把当前规格和地址伪造成下单时的历史快照。
ALTER TABLE product MODIFY COLUMN spec_definition JSON NOT NULL DEFAULT (JSON_ARRAY());
ALTER TABLE cart
    MODIFY COLUMN sku_id BIGINT NOT NULL,
    DROP INDEX uk_user_product,
    ADD UNIQUE KEY uk_user_sku(user_id,sku_id),
    ADD CONSTRAINT fk_cart_sku_product FOREIGN KEY(sku_id,product_id)
        REFERENCES product_sku(id,product_id) ON DELETE RESTRICT;
ALTER TABLE order_item
    MODIFY COLUMN sku_id BIGINT NOT NULL,
    MODIFY COLUMN sku_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT fk_order_item_sku_product FOREIGN KEY(sku_id,product_id)
        REFERENCES product_sku(id,product_id) ON DELETE RESTRICT;
