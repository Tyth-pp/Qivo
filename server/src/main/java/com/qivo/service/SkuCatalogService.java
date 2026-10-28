package com.qivo.service;

import com.qivo.dto.*;
import com.qivo.entity.*;

/** 商品规格维护及交易共用的验证；所有写入先锁商品，再锁 SKU。 */
public interface SkuCatalogService {
    void save(ProductSaveRequest request, boolean creating);

    void updateSku(Long productId, Long skuId, SkuUpdateRequest request);

    void adjustStock(Long productId, Long skuId, long delta);

    void adjustStock(Long productId, Long skuId, long delta, Long adminId);

    void hydrate(Product product);

    ProductSku lockSellable(Long skuId, int quantity);

    String snapshot(ProductSku sku);

    String specText(Product product, ProductSku sku);
}
