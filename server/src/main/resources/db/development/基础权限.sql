-- 仅由开发初始化命令在确认的空开发库执行；不包含账号密码，不属于 Flyway 自动迁移。
INSERT INTO `role`(id,name,code,description) VALUES
(1,'超级管理员','super_admin','商城全部管理权限'),
(2,'商品管理员','product_admin','商品维护权限');
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
