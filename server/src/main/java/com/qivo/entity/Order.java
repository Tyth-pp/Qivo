package com.qivo.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单主表 */
@Data
public class Order implements Serializable {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private Integer payStatus; // 0=未支付 1=已支付 2=已退款 3=已关闭
    private Integer shipStatus; // 0=未发货 1=已发货 2=已收货
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddressSnapshot;
    private String addressSnapshotSource;
    private String trackingNo;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
