CREATE TABLE IF NOT EXISTS `user` (id BIGINT PRIMARY KEY, username VARCHAR(50), password VARCHAR(100), status INT);
CREATE TABLE IF NOT EXISTS product_category (id BIGINT PRIMARY KEY, name VARCHAR(50), parent_id BIGINT DEFAULT 0, status INT DEFAULT 1, sort_order INT DEFAULT 0);
CREATE TABLE IF NOT EXISTS address (id BIGINT PRIMARY KEY, user_id BIGINT, receiver_name VARCHAR(50), receiver_phone VARCHAR(20), province VARCHAR(50), city VARCHAR(50), district VARCHAR(50), detail VARCHAR(255));
CREATE TABLE IF NOT EXISTS cart (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, product_id BIGINT, sku_id BIGINT, quantity INT, checked INT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(user_id,sku_id));
CREATE TABLE IF NOT EXISTS `order` (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) UNIQUE, user_id BIGINT, total_amount DECIMAL(10,2), pay_status INT, ship_status INT, address_id BIGINT,
 receiver_name VARCHAR(50), receiver_phone VARCHAR(20), receiver_address_snapshot VARCHAR(500), address_snapshot_source VARCHAR(16), tracking_no VARCHAR(100), remark VARCHAR(255), pay_time TIMESTAMP, ship_time TIMESTAMP, create_time TIMESTAMP, update_time TIMESTAMP
);
CREATE TABLE IF NOT EXISTS order_item (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT, product_id BIGINT, product_name VARCHAR(200), product_img VARCHAR(255), quantity INT, unit_price DECIMAL(10,2), total_price DECIMAL(10,2), sku_id BIGINT, sku_code VARCHAR(64), sku_spec_snapshot VARCHAR(4000), sku_snapshot_source VARCHAR(16));
