CREATE TABLE IF NOT EXISTS product (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), description TEXT,
 price DECIMAL(10,2), original_price DECIMAL(10,2), stock INT DEFAULT 0,
 status INT DEFAULT 1, category_id BIGINT, main_image VARCHAR(255), sales INT DEFAULT 0,
 spec_mode VARCHAR(16) DEFAULT 'SINGLE', spec_definition VARCHAR(4000) DEFAULT '[]',
 create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS product_sku (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT, sku_code VARCHAR(64), spec_key VARCHAR(255),
 spec_values VARCHAR(4000), price DECIMAL(10,2), original_price DECIMAL(10,2), stock INT,
 status VARCHAR(16), image VARCHAR(255), create_time TIMESTAMP, update_time TIMESTAMP
);

DROP TABLE IF EXISTS refund_attempt;
DROP TABLE IF EXISTS after_sale;
-- 仅新增记录，不改写历史业务行。
CREATE TABLE after_sale (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, order_no VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
 request_key VARCHAR(64) NOT NULL,
 reason VARCHAR(500) NOT NULL, status VARCHAR(24) NOT NULL, amount DECIMAL(10,2) NOT NULL,
 review_note VARCHAR(500), reviewer_id BIGINT, refunded_order_id BIGINT,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_after_sale_request(user_id,request_key), UNIQUE KEY uk_after_sale_refunded_order(refunded_order_id),
 KEY idx_after_sale_order(order_id,id), KEY idx_after_sale_status(status,id)
);
CREATE TABLE refund_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, after_sale_id BIGINT NOT NULL, attempt_no INT NOT NULL,
 order_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL, status VARCHAR(24) NOT NULL,
 message VARCHAR(500), operator_id BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_refund_attempt(after_sale_id,attempt_no)
);
