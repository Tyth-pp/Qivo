package com.qivo.dto;

import com.qivo.enums.ProductSpecMode;

import java.util.List;

/** 商品共同信息及新增 SKU；已有 SKU 的价格/状态/库存由独立接口维护。 */
public record ProductSaveRequest(
        Long id,
        String name,
        String description,
        Long categoryId,
        String mainImage,
        ProductSpecMode specMode,
        List<SkuSpecificationDefinition> specDefinitions,
        List<SkuDraftRequest> skus) {}
