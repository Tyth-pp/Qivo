-- 仅显式初始化空开发库时执行，不随启动生成商品和账号。
INSERT INTO product_sku(product_id,sku_code,spec_key,spec_values,price,original_price,stock,status,image)
SELECT id,CONCAT('QIVO-DEFAULT-',id),'DEFAULT',JSON_ARRAY(),price,original_price,stock,'ENABLED',main_image FROM product;
INSERT INTO cart(user_id,product_id,sku_id,quantity,checked)
SELECT 1,1,id,1,1 FROM product_sku WHERE product_id=1;
INSERT INTO cart(user_id,product_id,sku_id,quantity,checked)
SELECT 2,3,id,1,1 FROM product_sku WHERE product_id=3;
INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,address_id,remark,pay_time,ship_time,
receiver_name,receiver_phone,receiver_address_snapshot,address_snapshot_source)
SELECT 1,'QIVO-DEV-RECEIVED-001',1,79.00,1,2,1,'开发演示：已收货订单',NOW(),NOW(),
receiver_name,receiver_phone,CONCAT_WS(' ',province,city,district,detail),'CURRENT' FROM address WHERE id=1;
INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,address_id,remark,pay_time,
receiver_name,receiver_phone,receiver_address_snapshot,address_snapshot_source)
SELECT 2,'QIVO-DEV-PAID-002',2,89.00,1,0,2,'开发演示：待发货订单',NOW(),
receiver_name,receiver_phone,CONCAT_WS(' ',province,city,district,detail),'CURRENT' FROM address WHERE id=2;
INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,total_price,sku_id,sku_code,sku_spec_snapshot,sku_snapshot_source)
SELECT 1,2,'轻巧无线鼠标',1,79.00,79.00,id,sku_code,JSON_OBJECT('text','默认规格','selections',JSON_ARRAY()),'CURRENT'
FROM product_sku WHERE product_id=2;
INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,total_price,sku_id,sku_code,sku_spec_snapshot,sku_snapshot_source)
SELECT 2,4,'便携保温杯',1,89.00,89.00,id,sku_code,JSON_OBJECT('text','默认规格','selections',JSON_ARRAY()),'CURRENT'
FROM product_sku WHERE product_id=4;
INSERT INTO product_comment(user_id,product_id,order_id,rating,content) VALUES
(1,2,1,5,'开发演示评价：使用方便。');
