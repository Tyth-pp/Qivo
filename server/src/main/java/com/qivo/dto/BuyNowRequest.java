package com.qivo.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BuyNowRequest(
        @NotNull(message = "参数不能为空") @Positive(message = "标识必须为正数") Long addressId,
        @NotNull(message = "参数不能为空") @Positive(message = "标识必须为正数") Long skuId,
        @NotNull(message = "参数不能为空")
                @Min(value = 1, message = "数值低于允许范围")
                @Max(value = 999, message = "数值超过允许范围")
                Integer quantity,
        @Size(max = 255, message = "备注不能超过255字") String remark,
        @NotNull(message = "参数不能为空") @DecimalMin(value = "0.01", message = "确认金额必须大于零")
                BigDecimal expectedTotal) {}
