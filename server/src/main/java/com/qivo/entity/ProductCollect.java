package com.qivo.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ProductCollect implements Serializable {
    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createTime;
}
