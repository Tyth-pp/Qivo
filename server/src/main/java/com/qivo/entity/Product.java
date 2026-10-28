package com.qivo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qivo.dto.SkuSpecificationDefinition;
import com.qivo.enums.ProductSpecMode;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 商品主表 */
@TableName("product")
@Data
public class Product implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer status; // 1=上架 0=下架
    private Long categoryId;
    private String mainImage;
    private Integer sales;
    private ProductSpecMode specMode;
    private String specDefinition;

    @TableField(exist = false)
    private BigDecimal maxPrice;

    @TableField(exist = false)
    private Long totalStock;

    @TableField(exist = false)
    private Long defaultSkuId;

    @TableField(exist = false)
    private List<ProductSku> skus;

    @TableField(exist = false)
    private List<SkuSpecificationDefinition> specDefinitions;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
