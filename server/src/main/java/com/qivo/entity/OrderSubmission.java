package com.qivo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_submission")
public class OrderSubmission {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String requestKey;
    private String requestHash;
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
}
