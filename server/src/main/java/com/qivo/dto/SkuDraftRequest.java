package com.qivo.dto;

import com.qivo.enums.SkuStatus;
import java.math.BigDecimal;
import java.util.List;

/** SKU 草稿；规范组合键由服务端校验后生成，不接受客户端传入。 */
public record SkuDraftRequest(
        List<SkuSelection> specValues,
        BigDecimal price,
        BigDecimal originalPrice,
        Integer stock,
        SkuStatus status,
        String image) {}
