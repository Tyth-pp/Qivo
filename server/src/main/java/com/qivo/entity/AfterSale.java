package com.qivo.entity;
import com.qivo.enums.AfterSaleStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data
public class AfterSale {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private String requestKey;
    private String reason;
    private AfterSaleStatus status;
    private BigDecimal amount;
    private String reviewNote;
    private Long reviewerId;
    private Long refundedOrderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
