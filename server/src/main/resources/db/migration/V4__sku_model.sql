-- A 批演练草案：不在默认 db/migration 目录，不允许单独发布到运行库。
ALTER TABLE product
    ADD COLUMN spec_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN spec_definition JSON NULL,
    ADD CONSTRAINT ck_product_spec_mode CHECK (spec_mode IN ('SINGLE', 'MULTI')),
    ADD CONSTRAINT ck_product_spec_definition CHECK (JSON_TYPE(spec_definition) = 'ARRAY');

CREATE TABLE product_sku (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    spec_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    spec_values JSON NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NULL,
    stock INT NOT NULL DEFAULT 0,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENABLED',
    image VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_code (sku_code),
    UNIQUE KEY uk_product_spec (product_id, spec_key),
    -- 用于购物车/订单项的组合外键，保证 SKU 属于记录中的商品。
    UNIQUE KEY uk_sku_product (id, product_id),
    CONSTRAINT fk_sku_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE RESTRICT,
    CONSTRAINT ck_sku_price CHECK (price > 0),
    CONSTRAINT ck_sku_original_price CHECK (original_price IS NULL OR original_price >= price),
    CONSTRAINT ck_sku_stock CHECK (stock >= 0),
    CONSTRAINT ck_sku_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_sku_spec_values CHECK (JSON_TYPE(spec_values) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品销售版本（SKU）';

ALTER TABLE cart ADD COLUMN sku_id BIGINT NULL;
ALTER TABLE order_item
    ADD COLUMN sku_id BIGINT NULL,
    ADD COLUMN sku_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN sku_spec_snapshot JSON NULL,
    ADD COLUMN sku_snapshot_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'LEGACY',
    ADD CONSTRAINT ck_order_item_sku_source CHECK (sku_snapshot_source IN ('LEGACY', 'CURRENT'));
ALTER TABLE `order`
    ADD COLUMN receiver_name VARCHAR(50) NULL,
    ADD COLUMN receiver_phone VARCHAR(20) NULL,
    ADD COLUMN receiver_address_snapshot VARCHAR(512) NULL,
    ADD COLUMN address_snapshot_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'LEGACY',
    ADD CONSTRAINT ck_order_address_source CHECK (address_snapshot_source IN ('LEGACY', 'CURRENT'));
