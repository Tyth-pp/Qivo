-- 仅为已有角色补充并发版本；授权记录保留历史快照，不级联删除。
ALTER TABLE role ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE role_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(50) NOT NULL,
    before_snapshot LONGTEXT,
    after_snapshot LONGTEXT,
    note VARCHAR(255),
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_role_change (role_id, id),
    KEY idx_role_change_time (role_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色资料与授权变更记录';
