package com.qivo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qivo.cache.HomepageCache;
import com.qivo.constant.OrderConstant;
import com.qivo.constant.OrderMessageConstant;
import com.qivo.constant.SkuMessageConstant;
import com.qivo.entity.Address;
import com.qivo.entity.Cart;
import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.entity.Product;
import com.qivo.entity.ProductSku;
import com.qivo.enums.OrderPaymentStatus;
import com.qivo.enums.StockMovementKind;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.AddressMapper;
import com.qivo.mapper.AfterSaleMapper;
import com.qivo.mapper.CartMapper;
import com.qivo.mapper.OrderItemMapper;
import com.qivo.mapper.OrderMapper;
import com.qivo.mapper.ProductMapper;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.service.OrderClosureService;
import com.qivo.service.OrderService;
import com.qivo.service.SkuCatalogService;
import com.qivo.service.StockLedgerService;
import com.qivo.vo.PageVO;
import com.qivo.vo.AdminOrderDetailVO;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private static final int PAY_STATUS_UNPAID = OrderPaymentStatus.UNPAID.getCode();
    private static final int PAY_STATUS_PAID = OrderPaymentStatus.PAID.getCode();
    private static final int SHIP_STATUS_NOT_SHIPPED = 0;
    private static final int SHIP_STATUS_SHIPPED = 1;
    private static final int SHIP_STATUS_RECEIVED = 2;
    private static final int ORDER_PAY_TIMEOUT_MINUTES = OrderConstant.PAY_TIMEOUT_MINUTES;

    private final StockLedgerService ledger;
    private final AfterSaleMapper afterSales;
    private final OrderClosureService closure;
    private final ProductSkuMapper skuMapper;
    private final SkuCatalogService catalog;
    private final HomepageCache cache;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final AddressMapper addressMapper;

    public OrderServiceImpl(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper,
            CartMapper cartMapper,
            ProductMapper productMapper,
            AddressMapper addressMapper,
            ProductSkuMapper skuMapper,
            SkuCatalogService catalog,
            HomepageCache cache,
            StockLedgerService ledger,
            OrderClosureService closure, AfterSaleMapper afterSales) {
        this.afterSales = afterSales;
        this.ledger = ledger;
        this.closure = closure;
        this.skuMapper = skuMapper;
        this.catalog = catalog;
        this.cache = cache;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.addressMapper = addressMapper;
    }

    @Override
    @Transactional
    public Map<String, Object> create(
            Long userId,
            Long addressId,
            String remark,
            List<Long> cartItemIds,
            BigDecimal expectedTotal) {
        if (cartItemIds == null
                || cartItemIds.isEmpty()
                || cartItemIds.size() > 100
                || new HashSet<>(cartItemIds).size() != cartItemIds.size())
            throw new BusinessException(400, "请选择有效且不重复的购物车条目（最多 100 项）");
        cartMapper.lockOwner(userId);
        var selected =
                cartMapper.lockByUser(userId).stream()
                        .filter(c -> cartItemIds.contains(c.getId()))
                        .toList();
        if (selected.size() != cartItemIds.size())
            throw new BusinessException(400, "购物车条目不存在或不属于当前用户，请刷新结算页");
        var result = place(userId, addressId, remark, selected, expectedTotal);
        for (var c : selected) cartMapper.deleteById(c.getId(), userId);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> createBuyNow(
            Long userId,
            Long addressId,
            Long skuId,
            Integer quantity,
            String remark,
            BigDecimal expectedTotal) {
        if (quantity == null || skuId == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var sku = skuMapper.selectById(skuId);
        if (sku == null) throw new BusinessException(400, SkuMessageConstant.NOT_FOUND);
        var c = new Cart();
        c.setSkuId(skuId);
        c.setProductId(sku.getProductId());
        c.setQuantity(quantity);
        return place(userId, addressId, remark, List.of(c), expectedTotal);
    }

    private Map<String, Object> place(
            Long userId,
            Long addressId,
            String remark,
            List<Cart> requested,
            BigDecimal expectedTotal) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId))
            throw new BusinessException(OrderMessageConstant.INVALID_SHIPPING_ADDRESS);
        if (remark != null && remark.length() > 255)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        // Deterministic parent/SKU order shared by purchase and cancellation.
        var ordered =
                requested.stream()
                        .sorted(
                                Comparator.comparing(Cart::getProductId)
                                        .thenComparing(Cart::getSkuId))
                        .toList();
        var items = new ArrayList<OrderItem>();
        var reserved = new ArrayList<ProductSku>();
        BigDecimal total = BigDecimal.ZERO;
        for (var c : ordered) {
            if (c.getQuantity() == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
            ProductSku sku = catalog.lockSellable(c.getSkuId(), c.getQuantity());
            if (!sku.getProductId().equals(c.getProductId()))
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            Product product = productMapper.selectById(sku.getProductId());
            if (skuMapper.deduct(sku.getId(), c.getQuantity()) != 1)
                throw new BusinessException(ErrorCode.PRODUCT_STOCK_INSUFFICIENT);
            reserved.add(sku);
            var item = new OrderItem();
            item.setProductId(product.getId());
            item.setSkuId(sku.getId());
            item.setSkuCode(sku.getSkuCode());
            item.setSkuSpecSnapshot(catalog.snapshot(sku));
            item.setSkuSnapshotSource("CURRENT");
            item.setProductName(product.getName());
            item.setProductImg(
                    sku.getImage() == null || sku.getImage().isBlank()
                            ? product.getMainImage()
                            : sku.getImage());
            item.setQuantity(c.getQuantity());
            item.setUnitPrice(sku.getPrice());
            item.setTotalPrice(sku.getPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
            total = total.add(item.getTotalPrice());
            items.add(item);
        }
        if (total.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BusinessException(400, "订单金额超过上限，请拆单购买");
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0)
            throw new BusinessException(409, "商品价格已变化，请刷新结算页后重新确认");
        Order order = buildUnpaidOrder(userId, addressId, total, remark);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddressSnapshot(
                String.join(
                        " ",
                        address.getProvince(),
                        address.getCity(),
                        address.getDistrict(),
                        address.getDetail()));
        order.setAddressSnapshotSource("CURRENT");
        orderMapper.insert(order);
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var sku = reserved.get(i);
            ledger.record(
                    sku,
                    -item.getQuantity(),
                    StockMovementKind.ORDER_RESERVE,
                    order.getId(),
                    "reserve:" + order.getId() + ":" + sku.getId(),
                    userId,
                    "USER");
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }
        cache.evict();
        return Map.of(
                "orderId", order.getId(), "orderNo", order.getOrderNo(), "totalAmount", total);
    }

    @Override
    @Transactional
    public void pay(Long orderId, Long userId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        if (order.getPayStatus() == PAY_STATUS_PAID) return;
        if (isPaymentExpired(order)) {
            throw new BusinessException(
                    ErrorCode.ORDER_STATUS_ERROR.getCode(), OrderMessageConstant.PAYMENT_EXPIRED);
        }
        if (order.getPayStatus() != PAY_STATUS_UNPAID) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        if (orderMapper.payUnpaid(orderId) == 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        var paidItems =
                orderItemMapper.selectByOrderId(orderId).stream()
                        .sorted(Comparator.comparing(OrderItem::getProductId))
                        .toList();
        for (var item : paidItems)
            productMapper.increaseSales(item.getProductId(), item.getQuantity());
        cache.evict();
    }

    @Override
    public void cancel(Long orderId, Long userId) {
        closure.close(orderId, userId, false);
    }

    @Override
    public int cancelExpiredUnpaidOrders() {
        var expiredOrders =
                orderMapper.selectUnpaidBefore(
                        orderMapper.databaseTime().minusMinutes(ORDER_PAY_TIMEOUT_MINUTES));
        int count = 0;
        for (var order : expiredOrders) {
            try {
                if (closure.close(order.getId(), null, true)) count++;
            } catch (RuntimeException e) {
                log.error("超时关单失败，订单ID={}，本轮继续处理其他订单", order.getId(), e);
            }
        }
        return count;
    }

    @Override
    @Transactional
    public void confirm(Long orderId, Long userId) {
        Order order = orderMapper.lockById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        if (order.getPayStatus() != PAY_STATUS_PAID
                || order.getShipStatus() != SHIP_STATUS_SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        orderMapper.updateShipStatus(orderId, SHIP_STATUS_RECEIVED, null);
    }

    @Override
    public PageVO<Order> userPage(Long userId, int page, int pageSize, Integer status) {
        Page<Order> pagination = PageRequests.of(page, pageSize);
        List<Order> list = orderMapper.selectByUserId(pagination, userId, status);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    public List<OrderItem> getItems(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }

    @Override
    public PageVO<Order> page(
            int page,
            int pageSize,
            String orderNo,
            Integer status,
            String startTime,
            String endTime) {
        Page<Order> pagination = PageRequests.of(page, pageSize);
        List<Order> list = orderMapper.selectPage(pagination, orderNo, status, startTime, endTime);
        return PageVO.of(list, pagination.getTotal(), page, pageSize);
    }

    @Override
    public Order getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderDetailVO getAdminDetail(Long id) {
        return new AdminOrderDetailVO(getById(id), orderItemMapper.selectByOrderId(id));
    }

    @Override
    @Transactional
    public void updatePrice(Long id, BigDecimal totalAmount) {
        Order order = orderMapper.lockById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        if (order.getPayStatus() != PAY_STATUS_UNPAID
                || isPaymentExpired(order)
                || totalAmount == null
                || totalAmount.scale() > 2
                || totalAmount.signum() <= 0
                || totalAmount.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        orderMapper.updatePrice(id, totalAmount);
    }

    @Override
    @Transactional
    public void ship(Long id, String trackingNo) {
        Order order = orderMapper.lockById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        }
        if (order.getPayStatus() != PAY_STATUS_PAID
                || order.getShipStatus() != SHIP_STATUS_NOT_SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
        if (afterSales.activeCount(id)>0) throw new BusinessException(409,"该订单有处理中售后，暂不能发货");
        if (trackingNo==null || trackingNo.isBlank() || trackingNo.length()>100)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        orderMapper.updateShipStatus(id, SHIP_STATUS_SHIPPED, trackingNo.trim());
    }

    @Override
    public void handleRefund(Long id, boolean approve) {
        throw new BusinessException(409, OrderMessageConstant.AFTERSALE_UNAVAILABLE);
    }

    private Order buildUnpaidOrder(
            Long userId, Long addressId, BigDecimal totalAmount, String remark) {
        String orderNo = UUID.randomUUID().toString().replace("-", "");

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayStatus(PAY_STATUS_UNPAID);
        order.setShipStatus(SHIP_STATUS_NOT_SHIPPED);
        order.setAddressId(addressId);
        order.setRemark(remark);
        return order;
    }

    private boolean isPaymentExpired(Order order) {
        return order.getPayStatus() == PAY_STATUS_UNPAID
                && order.getCreateTime() != null
                && !order.getCreateTime()
                        .plusMinutes(ORDER_PAY_TIMEOUT_MINUTES)
                        .isAfter(orderMapper.databaseTime());
    }
}
