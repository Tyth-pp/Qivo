package com.qivo.service;
import java.math.BigDecimal;
/** 同步模拟通道；真实支付渠道必须独立设计验签、对账与不确定结果补偿。 */
public interface RefundGateway {
    record Outcome(boolean succeeded,String message) {}
    Outcome refund(String attemptKey,BigDecimal amount);
}
