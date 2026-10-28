package com.qivo.dto;

import java.util.List;

/** 商品内的一个规格维度；ID 不以展示标签生成。 */
public record SkuSpecificationDefinition(
        String id, String name, List<SkuSpecificationValue> values) {}
