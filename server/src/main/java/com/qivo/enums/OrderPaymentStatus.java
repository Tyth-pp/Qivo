package com.qivo.enums;

/** 保留既有数据库取值；关闭和支付只能从未支付状态转换。 */
public enum OrderPaymentStatus {
    UNPAID(0),
    PAID(1),
    REFUNDED(2),
    CANCELED(3);
    private final int code;

    OrderPaymentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
