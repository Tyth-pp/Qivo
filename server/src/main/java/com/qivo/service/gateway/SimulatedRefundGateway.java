package com.qivo.service.gateway;

import com.qivo.service.RefundGateway;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SimulatedRefundGateway implements RefundGateway {
    @Override
    public Outcome refund(String attemptKey, BigDecimal amount) {
        return new Outcome(true, "模拟退款成功，未发生真实资金退回");
    }
}
