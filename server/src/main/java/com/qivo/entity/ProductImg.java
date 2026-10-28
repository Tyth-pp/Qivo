package com.qivo.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class ProductImg implements Serializable {
    private Long id;
    private Long productId;
    private String imgUrl;
    private Integer sortOrder;
}
