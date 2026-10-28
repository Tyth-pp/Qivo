package com.qivo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.dto.BuyNowRequest;
import com.qivo.dto.CheckoutRequest;
import com.qivo.entity.OrderSubmission;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.CartMapper;
import com.qivo.mapper.OrderSubmissionMapper;
import com.qivo.service.OrderService;
import com.qivo.service.OrderSubmissionService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class OrderSubmissionServiceImpl implements OrderSubmissionService {
    private final CartMapper owners;
    private final OrderSubmissionMapper submissions;
    private final OrderService orders;
    private final ObjectMapper json;

    public OrderSubmissionServiceImpl(
            CartMapper owners,
            OrderSubmissionMapper submissions,
            OrderService orders,
            ObjectMapper json) {
        this.owners = owners;
        this.submissions = submissions;
        this.orders = orders;
        this.json = json;
    }

    @Override
    @Transactional
    public Map<String, Object> checkout(Long userId, String key, CheckoutRequest request) {
        return execute(
                userId,
                key,
                fingerprint(
                        "CART",
                        request.addressId(),
                        request.cartItemIds().stream().sorted().toList(),
                        request.expectedTotal().stripTrailingZeros().toPlainString(),
                        request.remark()),
                () ->
                        orders.create(
                                userId,
                                request.addressId(),
                                request.remark(),
                                request.cartItemIds(),
                                request.expectedTotal()));
    }

    @Override
    @Transactional
    public Map<String, Object> buyNow(Long userId, String key, BuyNowRequest request) {
        return execute(
                userId,
                key,
                fingerprint(
                        "BUY_NOW",
                        request.addressId(),
                        request.skuId(),
                        request.quantity(),
                        request.expectedTotal().stripTrailingZeros().toPlainString(),
                        request.remark()),
                () ->
                        orders.createBuyNow(
                                userId,
                                request.addressId(),
                                request.skuId(),
                                request.quantity(),
                                request.remark(),
                                request.expectedTotal()));
    }

    private String fingerprint(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            json.writeValueAsString(Arrays.asList(values))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("下单请求摘要生成失败", e);
        }
    }

    private Map<String, Object> execute(
            Long userId, String key, String hash, Supplier<Map<String, Object>> action) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,64}"))
            throw new BusinessException(400, "请提供16至64位下单幂等键");
        // 与购物车共享用户锁。先取得锁再读取回执，重复提交等待前一事务完成。
        if (owners.lockOwner(userId) == null) throw new BusinessException(401, "账号不存在");
        var receipt = submissions.find(userId, key);
        if (receipt != null) {
            if (!receipt.getRequestHash().equals(hash))
                throw new BusinessException(409, "同一下单幂等键不能用于不同请求，请重新确认订单");
            return Map.of(
                    "orderId",
                    receipt.getOrderId(),
                    "orderNo",
                    receipt.getOrderNo(),
                    "totalAmount",
                    receipt.getTotalAmount());
        }
        var result = action.get();
        receipt = new OrderSubmission();
        receipt.setUserId(userId);
        receipt.setRequestKey(key);
        receipt.setRequestHash(hash);
        receipt.setOrderId(((Number) result.get("orderId")).longValue());
        receipt.setOrderNo((String) result.get("orderNo"));
        receipt.setTotalAmount((BigDecimal) result.get("totalAmount"));
        submissions.insert(receipt);
        return result;
    }
}
