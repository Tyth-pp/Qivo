package com.qivo.constant;

/** 订单与售后的中文业务提示。 */
public final class OrderMessageConstant {

    private OrderMessageConstant() {}

    public static final String NO_SELECTED_PRODUCTS = "请先选择商品";
    public static final String INVALID_SHIPPING_ADDRESS = "收货地址无效";
    public static final String PRODUCT_OFF_SHELF = "商品已下架";
    public static final String INVALID_QUANTITY = "商品数量无效";
    public static final String AFTERSALE_UNAVAILABLE = "售后申请正在升级，暂未开放";
    public static final String PRODUCT_STOCK_INSUFFICIENT_PREFIX = "商品库存不足：";
    public static final String REMAINING_STOCK_INSUFFICIENT_PREFIX = "库存不足，剩余库存：";
    public static final String PAYMENT_EXPIRED = "订单超过10分钟未支付，请等待系统关闭";
}
