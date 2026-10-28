package com.qivo.service;

import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.vo.PageVO;
import com.qivo.vo.AdminOrderDetailVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface OrderService {

    // 后台管理
    PageVO<Order> page(
            int page,
            int pageSize,
            String orderNo,
            Integer status,
            String startTime,
            String endTime);

    Order getById(Long id);

    AdminOrderDetailVO getAdminDetail(Long id);

    void updatePrice(Long id, BigDecimal totalAmount);

    void ship(Long id, String trackingNo);

    void handleRefund(Long id, boolean approve);

    // 前台用户
    Map<String, Object> create(
            Long userId,
            Long addressId,
            String remark,
            List<Long> cartItemIds,
            BigDecimal expectedTotal);

    Map<String, Object> createBuyNow(
            Long userId,
            Long addressId,
            Long skuId,
            Integer quantity,
            String remark,
            BigDecimal expectedTotal);

    void pay(Long orderId, Long userId);

    void cancel(Long orderId, Long userId);

    int cancelExpiredUnpaidOrders();

    void confirm(Long orderId, Long userId);

    PageVO<Order> userPage(Long userId, int page, int pageSize, Integer status);

    List<OrderItem> getItems(Long orderId);
}
