package com.qivo.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "成功"),
    FAIL(500, "系统异常"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    CONFLICT(409, "业务状态冲突"),

    // 用户模块 1000~
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ACCOUNT_DISABLED(1003, "账号已被禁用"),
    USER_ALREADY_EXISTS(1004, "用户名已存在"),

    // 商品模块 2000~
    PRODUCT_NOT_EXIST(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    PRODUCT_STOCK_INSUFFICIENT(2003, "商品库存不足"),

    // 订单模块 3000~
    ORDER_NOT_EXIST(3001, "订单不存在"),
    ORDER_STATUS_ERROR(3002, "订单状态异常"),
    ORDER_CANNOT_CANCEL(3003, "订单无法取消"),

    // 购物车模块 4000~
    CART_ITEM_NOT_EXIST(4001, "购物车项不存在"),

    // 文件上传 5000~
    FILE_UPLOAD_FAILED(5001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(5002, "文件类型不允许");

    private final int code;
    private final String msg;
}
