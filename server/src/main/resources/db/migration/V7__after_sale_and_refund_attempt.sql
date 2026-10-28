-- 仅新增记录，不改写历史业务行。
CREATE TABLE after_sale (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, order_no VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reason VARCHAR(500) NOT NULL, status VARCHAR(24) NOT NULL, amount DECIMAL(10,2) NOT NULL,
 review_note VARCHAR(500), reviewer_id BIGINT, refunded_order_id BIGINT,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_after_sale_request(user_id,request_key), UNIQUE KEY uk_after_sale_refunded_order(refunded_order_id),
 KEY idx_after_sale_order(order_id,id), KEY idx_after_sale_status(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE refund_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, after_sale_id BIGINT NOT NULL, attempt_no INT NOT NULL,
 order_id BIGINT NOT NULL, amount DECIMAL(10,2) NOT NULL, status VARCHAR(24) NOT NULL,
 message VARCHAR(500), operator_id BIGINT NOT NULL,
 create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_refund_attempt(after_sale_id,attempt_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
