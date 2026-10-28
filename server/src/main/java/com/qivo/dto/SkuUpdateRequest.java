package com.qivo.dto;

import com.qivo.enums.SkuStatus;

import java.math.BigDecimal;

/** 不含库存，避免旧表单覆盖实时库存。 */
public record SkuUpdateRequest(
        BigDecimal price, BigDecimal originalPrice, SkuStatus status, String image) {}
