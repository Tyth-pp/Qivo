package com.qivo.constant;

public final class OrderConstant {
    private OrderConstant() {}

    public static final int PAY_TIMEOUT_MINUTES = 10;
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
}
