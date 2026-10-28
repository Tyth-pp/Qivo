package com.qivo.dto;

import jakarta.validation.constraints.*;

public record CartCheckRequest(
        @NotNull(message = "参数不能为空") @Positive(message = "标识必须为正数") Long cartId,
        @NotNull(message = "参数不能为空")
                @Min(value = 0, message = "数值低于允许范围")
                @Max(value = 1, message = "数值超过允许范围")
                Integer checked) {}
