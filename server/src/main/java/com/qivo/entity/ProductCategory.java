package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品分类表
 */
@Data
public class ProductCategory implements Serializable {

    private Long id;
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private String icon;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
