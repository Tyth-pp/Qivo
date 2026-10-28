package com.qivo.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ProductComment implements Serializable {
    private Long id;
    private Long userId;
    private String username;
    private Long productId;
    private Long orderId;
    private Integer rating;
    private String content;
    private String pics;
    private String appendContent;
    private LocalDateTime appendTime;
    private LocalDateTime createTime;
}
