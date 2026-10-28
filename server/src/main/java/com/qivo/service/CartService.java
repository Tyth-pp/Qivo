package com.qivo.service;

import com.qivo.vo.CartVO;

import java.util.List;

public interface CartService {
    List<CartVO> list(Long userId);

    void add(Long userId, Long skuId, Integer quantity);

    void update(Long cartId, Long userId, Integer quantity);

    void delete(Long cartId, Long userId);

    void check(Long cartId, Long userId, Integer checked);

    List<CartVO> getChecked(Long userId);
}
