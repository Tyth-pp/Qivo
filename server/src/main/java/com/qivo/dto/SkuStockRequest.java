package com.qivo.dto;

import jakarta.validation.constraints.*;

/** 增量补货/盘减，禁止绝对库存覆盖。 */
public record SkuStockRequest(
        @NotNull(message = "参数不能为空")
                @Min(value = -2147483647, message = "数值低于允许范围")
                @Max(value = 2147483647, message = "数值超过允许范围")
                Long delta) {}
