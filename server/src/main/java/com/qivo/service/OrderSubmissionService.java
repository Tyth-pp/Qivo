package com.qivo.service;

import com.qivo.dto.BuyNowRequest;
import com.qivo.dto.CheckoutRequest;

import java.util.Map;

public interface OrderSubmissionService {
    Map<String, Object> checkout(Long userId, String key, CheckoutRequest request);

    Map<String, Object> buyNow(Long userId, String key, BuyNowRequest request);
}
