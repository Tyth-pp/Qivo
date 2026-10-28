package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 购物车表 */
@Data
public class Cart implements Serializable {

    private Long id;
    private Long userId;
    private Long productId;
    private Long skuId;
    private Integer quantity;
    private Integer checked; // 是否勾选：1=勾选 0=未勾选
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
