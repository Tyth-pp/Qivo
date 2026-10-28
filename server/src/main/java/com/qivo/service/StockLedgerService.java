package com.qivo.service;

import com.qivo.entity.ProductSku;
import com.qivo.entity.StockMovement;
import com.qivo.enums.StockMovementKind;
import com.qivo.vo.PageVO;

public interface StockLedgerService {
    void record(
            ProductSku before,
            long delta,
            StockMovementKind kind,
            Long orderId,
            String eventKey,
            Long actorId,
            String actorDomain);

    PageVO<StockMovement> page(Long productId, Long skuId, int page, int pageSize);
}
