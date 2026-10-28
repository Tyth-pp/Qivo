package com.qivo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qivo.enums.SkuStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** SKU 持久化实体。specValues 是数据库 JSON 文本，业务校验使用有类型的 DTO。 */
@Data
@TableName("product_sku")
public class ProductSku {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String skuCode;
    private String specKey;
    private String specValues;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private SkuStatus status;
    private String image;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
