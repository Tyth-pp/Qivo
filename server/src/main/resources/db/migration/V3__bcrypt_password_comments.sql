-- 新开发库只使用 BCrypt；不转换或覆盖任何既有密码数据。
ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希';
ALTER TABLE `admin` MODIFY COLUMN `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希';
