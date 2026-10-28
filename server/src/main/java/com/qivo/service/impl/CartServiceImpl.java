package com.qivo.service.impl;

import com.qivo.constant.SkuMessageConstant;
import com.qivo.entity.*;
import com.qivo.enums.SkuStatus;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.*;
import com.qivo.result.ErrorCode;
import com.qivo.service.*;
import com.qivo.vo.CartVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    private final CartMapper carts;
    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final SkuCatalogService catalog;

    public CartServiceImpl(
            CartMapper carts,
            ProductMapper products,
            ProductSkuMapper skus,
            SkuCatalogService catalog) {
        this.carts = carts;
        this.products = products;
        this.skus = skus;
        this.catalog = catalog;
    }

    @Override
    public List<CartVO> list(Long userId) {
        return convert(carts.selectByUserId(userId));
    }

    @Override
    public List<CartVO> getChecked(Long userId) {
        return convert(carts.selectCheckedByUserId(userId));
    }

    @Override
    @Transactional
    public void add(Long userId, Long skuId, Integer quantity) {
        if (quantity == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
        // User row serializes cart mutations/checkout; product then SKU lock order follows catalog.
        lockUser(userId);
        var sku = catalog.lockSellable(skuId, quantity);
        var existing =
                carts.lockByUser(userId).stream()
                        .filter(c -> skuId.equals(c.getSkuId()))
                        .findFirst();
        long total = quantity + (existing.isPresent() ? existing.get().getQuantity() : 0L);
        if (total > 999 || total > sku.getStock())
            throw new BusinessException(400, SkuMessageConstant.CART_LIMIT);
        var c = new Cart();
        c.setUserId(userId);
        c.setProductId(sku.getProductId());
        c.setSkuId(skuId);
        c.setQuantity(quantity);
        carts.upsert(c);
    }

    private void lockUser(Long userId) {
        carts.lockOwner(userId);
    }

    @Override
    @Transactional
    public void update(Long cartId, Long userId, Integer quantity) {
        lockUser(userId);
        var c =
                carts.lockByUser(userId).stream()
                        .filter(row -> row.getId().equals(cartId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (quantity == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
        catalog.lockSellable(c.getSkuId(), quantity);
        carts.updateQuantity(cartId, userId, quantity);
    }

    @Override
    @Transactional
    public void delete(Long cartId, Long userId) {
        lockUser(userId);
        if (carts.deleteById(cartId, userId) == 0) throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    @Override
    @Transactional
    public void check(Long cartId, Long userId, Integer checked) {
        if (checked == null || (checked != 0 && checked != 1))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        lockUser(userId);
        if (carts.updateChecked(cartId, userId, checked) == 0)
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private List<CartVO> convert(List<Cart> rows) {
        if (rows.isEmpty()) return List.of();
        var productMap =
                products
                        .selectByIds(rows.stream().map(Cart::getProductId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));
        var skuMap =
                skus.selectByIds(rows.stream().map(Cart::getSkuId).distinct().toList()).stream()
                        .collect(Collectors.toMap(ProductSku::getId, s -> s));
        var result = new ArrayList<CartVO>();
        for (var c : rows) {
            var v = new CartVO();
            v.setId(c.getId());
            v.setUserId(c.getUserId());
            v.setProductId(c.getProductId());
            v.setSkuId(c.getSkuId());
            v.setQuantity(c.getQuantity());
            v.setChecked(c.getChecked());
            v.setCreateTime(c.getCreateTime());
            v.setUpdateTime(c.getUpdateTime());
            var p = productMap.get(c.getProductId());
            var s = skuMap.get(c.getSkuId());
            v.setProductOnShelf(false);
            if (p == null || s == null) v.setUnavailableReason(SkuMessageConstant.NOT_FOUND);
            else {
                v.setProductName(p.getName());
                v.setProductImage(
                        s.getImage() == null || s.getImage().isBlank()
                                ? p.getMainImage()
                                : s.getImage());
                v.setProductPrice(s.getPrice());
                v.setProductStock(s.getStock());
                v.setSpecText(catalog.specText(p, s));
                v.setProductOnShelf(p.getStatus() == 1 && s.getStatus() == SkuStatus.ENABLED);
                if (!v.getProductOnShelf()) v.setUnavailableReason(SkuMessageConstant.UNAVAILABLE);
                else if (c.getQuantity() > 999 || c.getQuantity() > s.getStock())
                    v.setUnavailableReason(SkuMessageConstant.INSUFFICIENT_OR_LIMIT);
            }
            result.add(v);
        }
        return result;
    }
}
