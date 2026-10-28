package com.qivo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qivo.entity.ProductSku;
import com.qivo.entity.StockMovement;
import com.qivo.enums.StockMovementKind;
import com.qivo.mapper.StockMovementMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.service.StockLedgerService;
import com.qivo.vo.PageVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockLedgerServiceImpl implements StockLedgerService {
    private final StockMovementMapper movements;

    public StockLedgerServiceImpl(StockMovementMapper movements) {
        this.movements = movements;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            ProductSku before,
            long delta,
            StockMovementKind kind,
            Long orderId,
            String eventKey,
            Long actorId,
            String actorDomain) {
        long after = before.getStock() + delta;
        if (after < 0 || after > Integer.MAX_VALUE) throw new IllegalStateException("库存流水余额越界");
        var row = new StockMovement();
        row.setProductId(before.getProductId());
        row.setSkuId(before.getId());
        row.setOrderId(orderId);
        row.setKind(kind);
        row.setDelta(delta);
        row.setStockBefore(before.getStock());
        row.setStockAfter((int) after);
        row.setEventKey(eventKey);
        row.setActorId(actorId);
        row.setActorDomain(actorDomain);
        movements.insert(row);
    }

    @Override
    public PageVO<StockMovement> page(Long productId, Long skuId, int page, int pageSize) {
        var pagination = PageRequests.<StockMovement>of(page, pageSize);
        movements.selectPage(
                pagination,
                new LambdaQueryWrapper<StockMovement>()
                        .eq(StockMovement::getProductId, productId)
                        .eq(skuId != null, StockMovement::getSkuId, skuId)
                        .orderByDesc(StockMovement::getId));
        return PageVO.of(pagination.getRecords(), pagination.getTotal(), page, pageSize);
    }
}
