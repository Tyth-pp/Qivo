package com.qivo.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutRequest(
        @NotNull(message = "参数不能为空") @Positive(message = "标识必须为正数") Long addressId,
        @NotEmpty(message = "请选择购物车条目") @Size(max = 100, message = "最多结算100项")
                List<@NotNull(message = "参数不能为空") @Positive(message = "标识必须为正数") Long> cartItemIds,
        @Size(max = 255, message = "备注不能超过255字") String remark,
        @NotNull(message = "参数不能为空") @DecimalMin(value = "0.01", message = "确认金额必须大于零")
                BigDecimal expectedTotal) {}
