-- 仅由开发初始化命令在确认的空开发库执行；不包含账号密码，不属于 Flyway 自动迁移。
INSERT INTO `role`(id,name,code,description) VALUES
(1,'超级管理员','super_admin','开发环境全部权限'),
(2,'商品管理员','product_admin','开发环境商品管理权限');
INSERT INTO `permission`(id,name,code,parent_id,type,sort_order) VALUES
(1,'仪表盘','dashboard',0,1,1),
(2,'商品管理','product',0,1,2),
(3,'订单管理','order',0,1,3),
(4,'用户管理','user',0,1,4),
(5,'系统管理','system',0,1,5),
(6,'商品列表','product:list',2,1,1),
(7,'商品分类','category:list',2,1,2),
(8,'新增商品','product:add',2,2,3),
(9,'修改商品','product:update',2,2,4),
(10,'删除商品','product:delete',2,2,5),
(11,'订单列表','order:list',3,1,1),
(12,'订单发货','order:ship',3,2,2),
(13,'处理售后','order:refund',3,2,3),
(14,'用户列表','user:list',4,1,1),
(15,'用户禁用','user:disable',4,2,2),
(16,'角色管理','role:list',5,1,1),
(17,'管理员列表','admin:list',5,1,2);
INSERT INTO admin_role(admin_id,role_id) VALUES (1,1),(2,2);
INSERT INTO role_permission(role_id,permission_id) SELECT 1,id FROM permission;
INSERT INTO role_permission(role_id,permission_id) SELECT 2,id FROM permission
WHERE code IN ('dashboard','product','product:list','category:list','product:add','product:update','product:delete');
INSERT INTO product_category(id,name,parent_id,sort_order,status) VALUES
(1,'数码办公',0,1,1),(2,'居家生活',0,2,1),(3,'键盘鼠标',1,1,1),(4,'日用好物',2,1,1);
INSERT INTO product(id,name,description,price,original_price,stock,status,category_id,sales) VALUES
(1,'栖物机械键盘','开发演示商品：紧凑布局，有线连接。',199.00,259.00,50,1,3,0),
(2,'轻巧无线鼠标','开发演示商品：适用于日常办公。',79.00,99.00,80,1,3,1),
(3,'桌面收纳盒','开发演示商品：整理桌面小物。',39.00,49.00,100,1,4,0),
(4,'便携保温杯','开发演示商品：日常随行。',89.00,119.00,60,1,4,1),
(5,'待上架示例商品','用于验证下架商品不可购买。',19.00,29.00,10,0,4,0);
INSERT INTO address(id,user_id,receiver_name,receiver_phone,province,city,district,detail,is_default) VALUES
(1,1,'开发用户','13800000001','浙江省','杭州市','西湖区','演示地址一号（非真实地址）',1),
(2,2,'另一开发用户','13800000002','浙江省','杭州市','西湖区','演示地址二号（非真实地址）',1);
INSERT INTO product_collect(user_id,product_id) VALUES (1,3);
