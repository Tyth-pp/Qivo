package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderItem implements Serializable {
    private Long id;
    private Long orderId;
    private Long productId;
    private Long skuId;
    private String skuCode;
    private String skuSpecSnapshot;
    private String skuSnapshotSource;
    private String productName;
    private String productImg;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
}
