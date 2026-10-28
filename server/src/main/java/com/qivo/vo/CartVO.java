package com.qivo.vo;

import com.qivo.entity.Cart;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class CartVO extends Cart {

    private String specText;
    private String unavailableReason;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer productStock;
    private Boolean productOnShelf;
}
