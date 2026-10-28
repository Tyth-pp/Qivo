package com.qivo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qivo.enums.StockMovementKind;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_movement")
public class StockMovement {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private Long skuId;
    private Long orderId;
    private String eventKey;
    private StockMovementKind kind;
    private Long delta;
    private Integer stockBefore;
    private Integer stockAfter;
    private Long actorId;
    private String actorDomain;
    private LocalDateTime createTime;
}
