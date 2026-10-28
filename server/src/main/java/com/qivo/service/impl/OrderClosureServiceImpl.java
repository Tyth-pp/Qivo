package com.qivo.service.impl;

import com.qivo.cache.HomepageCache;
import com.qivo.constant.OrderConstant;
import com.qivo.entity.OrderItem;
import com.qivo.enums.OrderPaymentStatus;
import com.qivo.enums.StockMovementKind;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.OrderItemMapper;
import com.qivo.mapper.OrderMapper;
import com.qivo.mapper.ProductMapper;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.result.ErrorCode;
import com.qivo.service.OrderClosureService;
import com.qivo.service.StockLedgerService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Service
public class OrderClosureServiceImpl implements OrderClosureService {
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final StockLedgerService ledger;
    private final HomepageCache cache;

    public OrderClosureServiceImpl(
            OrderMapper orders,
            OrderItemMapper items,
            ProductMapper products,
            ProductSkuMapper skus,
            StockLedgerService ledger,
            HomepageCache cache) {
        this.orders = orders;
        this.items = items;
        this.products = products;
        this.skus = skus;
        this.ledger = ledger;
        this.cache = cache;
    }

    @Override
    @Transactional
    public boolean close(Long orderId, Long userId, boolean expiredOnly) {
        var order = orders.lockById(orderId);
        if (order == null || (userId != null && !order.getUserId().equals(userId)))
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        if (order.getPayStatus() == OrderPaymentStatus.CANCELED.getCode()) return false;
        if (order.getPayStatus() != OrderPaymentStatus.UNPAID.getCode()
                || order.getShipStatus() != 0) {
            if (expiredOnly) return false;
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL);
        }
        if (expiredOnly
                && order.getCreateTime()
                        .plusMinutes(OrderConstant.PAY_TIMEOUT_MINUTES)
                        .isAfter(orders.databaseTime())) return false;
        if (orders.cancelUnpaid(orderId) != 1)
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        var ordered =
                items.selectByOrderId(orderId).stream()
                        .sorted(
                                Comparator.comparing(OrderItem::getProductId)
                                        .thenComparing(
                                                OrderItem::getSkuId,
                                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
        for (var item : ordered) {
            products.lockById(item.getProductId());
            var sku = item.getSkuId() == null ? null : skus.lockById(item.getSkuId());
            if (sku == null
                    || !sku.getProductId().equals(item.getProductId())
                    || skus.adjust(sku.getId(), item.getQuantity()) != 1)
                throw new BusinessException(409, "库存恢复失败，请检查规格及库存上限");
            ledger.record(
                    sku,
                    item.getQuantity(),
                    StockMovementKind.ORDER_RELEASE,
                    orderId,
                    "release:" + orderId + ":" + sku.getId(),
                    userId,
                    expiredOnly ? "SYSTEM" : "USER");
        }
        cache.evict();
        return true;
    }
}
