package com.qivo.constant;

/** 商品规格校验提示，通用错误复用 ErrorCode。 */
public final class SkuMessageConstant {
    private SkuMessageConstant() {}

    public static final String INVALID_DEFINITION = "商品规格定义不完整或不合法";
    public static final String INVALID_COMBINATION = "SKU规格必须完整且属于当前商品";
    public static final String DUPLICATE_COMBINATION = "同一商品不能包含重复的SKU规格组合";
    public static final String INVALID_DEFAULT = "无规格商品必须且只能包含一个默认SKU";
    public static final String INVALID_PRICE_STOCK = "SKU价格、库存或启用状态不合法";
    public static final String INVALID_IMAGE = "SKU图片地址长度不能超过255字符";
    public static final String NOT_FOUND = "商品规格不存在";
    public static final String UNAVAILABLE = "商品已下架或规格已禁用";
    public static final String WRONG_PRODUCT = "SKU 不属于当前商品";
    public static final String INVALID_QUANTITY = "请选择规格，数量应在 1 到 999 之间";
    public static final String CART_LIMIT = "合并后的数量超过库存或 999 件上限";
    public static final String INSUFFICIENT_OR_LIMIT = "库存不足或数量超过上限";
}
