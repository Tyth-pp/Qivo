package com.qivo.constant;

/** 商品评价与追评的业务提示。 */
public final class CommentMessageConstant {

    private CommentMessageConstant() {}

    public static final String INVALID_RATING = "评分必须在1-5之间";
    public static final String ORDER_INFORMATION_REQUIRED = "评价订单信息不完整";
    public static final String RECEIPT_REQUIRED = "确认收货后才能评价";
    public static final String PRODUCT_NOT_IN_ORDER = "该商品不属于当前订单";
    public static final String ALREADY_REVIEWED = "该商品已评价";
    public static final String COMMENT_NOT_FOUND = "评价不存在";
    public static final String ALREADY_APPENDED = "已追评，不能重复追评";
    public static final String APPEND_CONTENT_REQUIRED = "追评内容不能为空";
}
