-- 仅扩展字段，不改写历史哈希；成功登录后再迁移 BCrypt。
ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) NOT NULL COMMENT '密码哈希';
ALTER TABLE `admin` MODIFY COLUMN `password` VARCHAR(255) NOT NULL COMMENT '密码哈希';
