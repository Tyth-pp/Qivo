-- 库存流水以迁移时库存为期初，不伪造历史库存变动。
CREATE TABLE stock_movement (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, order_id BIGINT NULL,
 event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 kind VARCHAR(24) NOT NULL, delta BIGINT NOT NULL,
 stock_before INT NOT NULL, stock_after INT NOT NULL,
 actor_id BIGINT NULL, actor_domain VARCHAR(16) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_stock_event (event_key),
 KEY idx_stock_sku (sku_id,id), KEY idx_stock_order (order_id),
 CONSTRAINT ck_stock_movement_balance CHECK (stock_after = stock_before + delta AND stock_before >= 0 AND stock_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO stock_movement(product_id,sku_id,event_key,kind,delta,stock_before,stock_after,actor_domain)
 SELECT product_id,id,CONCAT('initial:',id),'INITIAL',stock,0,stock,'MIGRATION' FROM product_sku;

-- 仅保存成功回执，与订单及库存同事务提交；失败回滚后允许原键重试。
CREATE TABLE order_submission (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 order_id BIGINT NOT NULL, order_no VARCHAR(64) NOT NULL, total_amount DECIMAL(10,2) NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_submission_user_key (user_id,request_key),
 UNIQUE KEY uk_submission_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
