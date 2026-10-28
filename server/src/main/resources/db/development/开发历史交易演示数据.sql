-- 供 V3 副本迁移演练使用，运行时按实际结构选择。
INSERT INTO cart(user_id,product_id,quantity,checked) VALUES (1,1,1,1),(2,3,1,1);
INSERT INTO `order`(id,order_no,user_id,total_amount,pay_status,ship_status,address_id,remark,pay_time,ship_time) VALUES
(1,'QIVO-DEV-RECEIVED-001',1,79.00,1,2,1,'开发演示：已收货订单',NOW(),NOW()),
(2,'QIVO-DEV-PAID-002',2,89.00,1,0,2,'开发演示：待发货订单',NOW(),NULL);
INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price,total_price) VALUES
(1,2,'轻巧无线鼠标',1,79.00,79.00),(2,4,'便携保温杯',1,89.00,89.00);
INSERT INTO product_comment(user_id,product_id,order_id,rating,content) VALUES
(1,2,1,5,'开发演示评价：使用方便。');
