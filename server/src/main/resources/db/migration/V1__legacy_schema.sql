-- 新空库结构；旧库经校验后显式 baseline 到版本 1。
CREATE TABLE `user` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    `username`      VARCHAR(50)     NOT NULL                 COMMENT '用户名',
    `password`      VARCHAR(64)     NOT NULL                 COMMENT '密码（MD5加盐）',
    `email`         VARCHAR(100)    DEFAULT NULL             COMMENT '邮箱',
    `phone`         VARCHAR(20)     DEFAULT NULL             COMMENT '手机号',
    `avatar`        VARCHAR(255)    DEFAULT NULL             COMMENT '头像URL',
    `score`         INT             NOT NULL DEFAULT 0       COMMENT '积分',
    `status`        TINYINT         NOT NULL DEFAULT 1       COMMENT '状态：1=正常 0=禁用',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `update_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='买家用户表';

CREATE TABLE `admin` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '管理员ID',
    `username`        VARCHAR(50)   NOT NULL                 COMMENT '用户名',
    `password`        VARCHAR(64)   NOT NULL                 COMMENT '密码（MD5加盐）',
    `real_name`       VARCHAR(50)   DEFAULT NULL             COMMENT '真实姓名',
    `status`          TINYINT       NOT NULL DEFAULT 1       COMMENT '状态：1=正常 0=禁用',
    `last_login_time` DATETIME      DEFAULT NULL             COMMENT '最后登录时间',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

CREATE TABLE `role` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '角色ID',
    `name`        VARCHAR(50)   NOT NULL                 COMMENT '角色名称',
    `code`        VARCHAR(50)   NOT NULL                 COMMENT '角色编码（shiro权限标识）',
    `description` VARCHAR(255)  DEFAULT NULL             COMMENT '角色描述',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE `permission` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '权限ID',
    `name`        VARCHAR(50)   NOT NULL                 COMMENT '权限名称',
    `code`        VARCHAR(100)  NOT NULL                 COMMENT '权限编码（如 product:add）',
    `url`         VARCHAR(255)  DEFAULT NULL             COMMENT '接口路径',
    `parent_id`   BIGINT        NOT NULL DEFAULT 0       COMMENT '父权限ID（0=顶级菜单）',
    `type`        TINYINT       NOT NULL DEFAULT 1       COMMENT '类型：1=菜单 2=按钮 3=接口',
    `sort_order`  INT           DEFAULT 0                COMMENT '排序号',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE `admin_role` (
    `id`       BIGINT NOT NULL AUTO_INCREMENT,
    `admin_id` BIGINT NOT NULL COMMENT '管理员ID',
    `role_id`  BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色中间表';

CREATE TABLE `role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限中间表';

CREATE TABLE `product_category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    `name`        VARCHAR(50)  NOT NULL                 COMMENT '分类名称',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0       COMMENT '父分类ID（0=一级分类）',
    `sort_order`  INT          DEFAULT 0                COMMENT '排序号',
    `icon`        VARCHAR(255) DEFAULT NULL             COMMENT '分类图标',
    `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1=显示 0=隐藏',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

CREATE TABLE `product` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT  COMMENT '商品ID',
    `name`           VARCHAR(200)   NOT NULL                 COMMENT '商品名称',
    `description`    TEXT           DEFAULT NULL             COMMENT '商品描述',
    `price`          DECIMAL(10,2)  NOT NULL                 COMMENT '销售价格',
    `original_price` DECIMAL(10,2)  DEFAULT NULL             COMMENT '原价（划线价）',
    `stock`          INT            NOT NULL DEFAULT 0       COMMENT '库存数量',
    `status`         TINYINT        NOT NULL DEFAULT 1       COMMENT '状态：1=上架 0=下架',
    `category_id`    BIGINT         NOT NULL                 COMMENT '所属分类ID',
    `main_image`     VARCHAR(255)   DEFAULT NULL             COMMENT '商品主图URL',
    `sales`          INT            NOT NULL DEFAULT 0       COMMENT '销量',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status` (`status`),
    KEY `idx_name` (`name`),
    KEY `idx_sales` (`sales`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品主表';

CREATE TABLE `product_img` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '图片ID',
    `product_id` BIGINT       NOT NULL                 COMMENT '商品ID',
    `img_url`    VARCHAR(255) NOT NULL                 COMMENT '图片URL',
    `sort_order` INT          DEFAULT 0                COMMENT '排序号（数字越小越靠前）',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片附表';

CREATE TABLE `cart` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '购物车项ID',
    `user_id`     BIGINT   NOT NULL                 COMMENT '用户ID',
    `product_id`  BIGINT   NOT NULL                 COMMENT '商品ID',
    `quantity`    INT      NOT NULL DEFAULT 1       COMMENT '购买数量',
    `checked`     TINYINT  NOT NULL DEFAULT 1       COMMENT '是否勾选：1=勾选 0=未勾选',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

CREATE TABLE `address` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '地址ID',
    `user_id`       BIGINT       NOT NULL                 COMMENT '用户ID',
    `receiver_name` VARCHAR(50)  NOT NULL                 COMMENT '收件人姓名',
    `receiver_phone` VARCHAR(20) NOT NULL                 COMMENT '收件人电话',
    `province`      VARCHAR(50)  NOT NULL                 COMMENT '省份',
    `city`          VARCHAR(50)  NOT NULL                 COMMENT '城市',
    `district`      VARCHAR(50)  NOT NULL                 COMMENT '区/县',
    `detail`        VARCHAR(255) NOT NULL                 COMMENT '详细地址',
    `is_default`    TINYINT      NOT NULL DEFAULT 0       COMMENT '是否默认地址：1=是 0=否',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

CREATE TABLE `order` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `order_no`      VARCHAR(32)    NOT NULL                 COMMENT '订单号',
    `user_id`       BIGINT         NOT NULL                 COMMENT '用户ID',
    `total_amount`  DECIMAL(10,2)  NOT NULL                 COMMENT '订单总金额',
    `pay_status`    TINYINT        NOT NULL DEFAULT 0       COMMENT '支付状态：0=未支付 1=已支付 2=已退款 3=已取消',
    `ship_status`   TINYINT        NOT NULL DEFAULT 0       COMMENT '物流状态：0=未发货 1=已发货 2=已收货',
    `address_id`    BIGINT         NOT NULL                 COMMENT '收货地址ID',
    `tracking_no`   VARCHAR(50)    DEFAULT NULL             COMMENT '物流单号',
    `remark`        VARCHAR(500)   DEFAULT NULL             COMMENT '订单备注',
    `pay_time`      DATETIME       DEFAULT NULL             COMMENT '支付时间',
    `ship_time`     DATETIME       DEFAULT NULL             COMMENT '发货时间',
    `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_pay_status` (`pay_status`),
    KEY `idx_ship_status` (`ship_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE `order_item` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '订单项ID',
    `order_id`       BIGINT        NOT NULL                 COMMENT '订单ID（关联订单主表）',
    `product_id`     BIGINT        NOT NULL                 COMMENT '商品ID',
    `product_name`   VARCHAR(200)  NOT NULL                 COMMENT '商品名称（快照）',
    `product_img`    VARCHAR(255)  DEFAULT NULL             COMMENT '商品图片（快照）',
    `quantity`       INT           NOT NULL DEFAULT 1       COMMENT '购买数量',
    `unit_price`     DECIMAL(10,2) NOT NULL                 COMMENT '下单时单价',
    `total_price`    DECIMAL(10,2) NOT NULL                 COMMENT '小计金额',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项表';

CREATE TABLE `banner` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '轮播图ID',
    `title`       VARCHAR(100) DEFAULT NULL             COMMENT '标题',
    `img_url`     VARCHAR(255) NOT NULL                 COMMENT '图片URL',
    `link_url`    VARCHAR(255) DEFAULT NULL             COMMENT '跳转链接',
    `sort_order`  INT          DEFAULT 0                COMMENT '排序号',
    `status`      TINYINT      NOT NULL DEFAULT 1       COMMENT '状态：1=启用 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图表';

CREATE TABLE `product_collect` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '收藏ID',
    `user_id`     BIGINT   NOT NULL                 COMMENT '用户ID',
    `product_id`  BIGINT   NOT NULL                 COMMENT '商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品收藏表';

CREATE TABLE `product_comment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    `user_id`      BIGINT       NOT NULL                 COMMENT '用户ID',
    `product_id`   BIGINT       NOT NULL                 COMMENT '商品ID',
    `order_id`     BIGINT       NOT NULL                 COMMENT '订单ID',
    `rating`       TINYINT      NOT NULL DEFAULT 5       COMMENT '评分：1~5',
    `content`      VARCHAR(500) DEFAULT NULL             COMMENT '评价内容',
    `pics`         VARCHAR(1000) DEFAULT NULL            COMMENT '评价图片（逗号分隔）',
    `append_content` VARCHAR(500) DEFAULT NULL           COMMENT '追评内容',
    `append_time`    DATETIME    DEFAULT NULL             COMMENT '追评时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';
