-- 管理员个人头像；保留已有账号与权限。
ALTER TABLE admin ADD COLUMN avatar VARCHAR(255) NULL;
