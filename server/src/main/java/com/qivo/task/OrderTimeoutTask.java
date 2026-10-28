package com.qivo.task;

import com.qivo.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "qivo.order-timeout.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OrderTimeoutTask {

    private final OrderService orderService;

    public OrderTimeoutTask(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cancelExpiredUnpaidOrders() {
        orderService.cancelExpiredUnpaidOrders();
    }
}
