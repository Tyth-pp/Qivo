package com.qivo.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qivo.cache.HomepageCache;
import com.qivo.entity.AfterSale;
import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.entity.RefundAttempt;
import com.qivo.enums.AfterSaleStatus;
import com.qivo.enums.StockMovementKind;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.AfterSaleMapper;
import com.qivo.mapper.OrderItemMapper;
import com.qivo.mapper.OrderMapper;
import com.qivo.mapper.ProductMapper;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.mapper.RefundAttemptMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.service.AfterSaleService;
import com.qivo.service.RefundGateway;
import com.qivo.service.StockLedgerService;
import com.qivo.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service
public class AfterSaleServiceImpl implements AfterSaleService {
    private final AfterSaleMapper sales;
    private final RefundAttemptMapper attempts;
    private final OrderMapper orders;
    private final OrderItemMapper items;
    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final StockLedgerService ledger;
    private final RefundGateway gateway;
    private final HomepageCache cache;
    public AfterSaleServiceImpl(AfterSaleMapper sales,RefundAttemptMapper attempts,OrderMapper orders,
            OrderItemMapper items,ProductMapper products,ProductSkuMapper skus,
            StockLedgerService ledger,RefundGateway gateway,HomepageCache cache) {
        this.sales=sales;this.attempts=attempts;this.orders=orders;this.items=items;
        this.products=products;this.skus=skus;this.ledger=ledger;this.gateway=gateway;this.cache=cache;
    }
    private Order owned(Long id,Long userId,boolean lock) {
        var order=lock?orders.lockById(id):orders.selectById(id);
        if(order==null||(userId!=null&&!userId.equals(order.getUserId())))
            throw new BusinessException(ErrorCode.ORDER_NOT_EXIST);
        return order;
    }
    private void refundable(Order order) {
        if(order.getPayStatus()!=1||order.getShipStatus()!=0)
            throw new BusinessException(409,"仅支持已付款、未发货订单的整单退款");
    }
    @Override @Transactional
    public AfterSale apply(Long userId,Long orderId,String key,String reason) {
        if(reason==null||reason.isBlank()||reason.length()>500||key==null||!key.matches("[a-zA-Z0-9_-]{8,64}"))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        var order=owned(orderId,userId,true);
        var previous=sales.byRequest(userId,key);
        if(previous!=null) {
            if(!previous.getOrderId().equals(orderId)||!previous.getReason().equals(reason.trim()))
                throw new BusinessException(409,"同一申请标识不能用于不同内容");
            return previous;
        }
        refundable(order);
        if(sales.activeCount(orderId)>0)throw new BusinessException(409,"该订单已有处理中售后，请查看申请记录");
        var row=new AfterSale();row.setOrderId(orderId);row.setOrderNo(order.getOrderNo());row.setUserId(userId);row.setRequestKey(key);
        row.setReason(reason.trim());row.setAmount(order.getTotalAmount());row.setStatus(AfterSaleStatus.REQUESTED);
        sales.insert(row);return sales.selectById(row.getId());
    }
    @Override public List<AfterSale> history(Long userId,Long orderId) {
        owned(orderId,userId,false);return sales.forOrder(orderId);
    }
    @Override public PageVO<AfterSale> page(int page,int pageSize,String status,String orderNo) {
        var query=new LambdaQueryWrapper<AfterSale>();
        if(status!=null&&!status.isBlank()) {
            try{query.eq(AfterSale::getStatus,AfterSaleStatus.valueOf(status));}
            catch(IllegalArgumentException e){throw new BusinessException(ErrorCode.BAD_REQUEST);}
        }
        query.like(orderNo!=null&&!orderNo.isBlank(),AfterSale::getOrderNo,orderNo).orderByDesc(AfterSale::getId);
        var result=sales.selectPage(PageRequests.<AfterSale>of(page,pageSize),query);
        return PageVO.of(result.getRecords(),result.getTotal(),page,pageSize);
    }
    @Override public List<RefundAttempt> attempts(Long id) {
        if(sales.selectById(id)==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        return attempts.forAfterSale(id);
    }
    @Override @Transactional
    public AfterSale review(Long id,Long operator,boolean approve,String note) {
        var initial=sales.selectById(id);
        if(initial==null)throw new BusinessException(ErrorCode.NOT_FOUND);
        var order=owned(initial.getOrderId(),null,true); // 与申请和发货共用订单锁；订单→商品→规格。
        var row=sales.lockById(id);
        if(row.getStatus()==AfterSaleStatus.REFUNDED) {
            if(!approve)throw new BusinessException(409,"退款已经完成，不能拒绝");
            return row;
        }
        if(row.getStatus()==AfterSaleStatus.REJECTED) {
            if(approve)throw new BusinessException(409,"已拒绝的申请不能批准，请由买家重新申请");
            return row;
        }
        refundable(order);
        if(row.getStatus()!=AfterSaleStatus.REQUESTED&&row.getStatus()!=AfterSaleStatus.REFUND_FAILED)
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        row.setReviewerId(operator);row.setReviewNote(note==null?"":note.trim());
        if(!approve) {
            if(row.getStatus()!=AfterSaleStatus.REQUESTED||row.getReviewNote().isBlank())
                throw new BusinessException(409,"拒绝需要填写原因，退款失败请重试原申请");
            row.setStatus(AfterSaleStatus.REJECTED);sales.saveReview(row);return sales.selectById(id);
        }
        var attempt=new RefundAttempt();attempt.setAfterSaleId(id);attempt.setOrderId(order.getId());
        attempt.setAttemptNo(attempts.forAfterSale(id).size()+1);attempt.setAmount(row.getAmount());attempt.setOperatorId(operator);
        var outcome=gateway.refund(id+":"+attempt.getAttemptNo(),row.getAmount());
        attempt.setStatus(outcome.succeeded()?"SUCCEEDED":"FAILED");attempt.setMessage(outcome.message());
        attempts.insert(attempt);
        if(!outcome.succeeded()) {
            row.setStatus(AfterSaleStatus.REFUND_FAILED);sales.saveReview(row);return sales.selectById(id);
        }
        if(orders.refundPaid(order.getId())!=1)throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        var storedItems=items.selectByOrderId(order.getId());
        if(storedItems.isEmpty()||storedItems.stream().anyMatch(item->item.getSkuId()==null||item.getProductId()==null||item.getQuantity()==null||item.getQuantity()<=0))
            throw new BusinessException(409,"订单商品明细不完整，不能自动回补库存");
        var lines=storedItems.stream().sorted(Comparator.comparing(OrderItem::getProductId).thenComparing(OrderItem::getSkuId)).toList();
        for(var item:lines) {
            products.lockById(item.getProductId());var sku=skus.lockById(item.getSkuId());
            if(sku==null||!sku.getProductId().equals(item.getProductId())
                    ||(long)sku.getStock()+item.getQuantity()>Integer.MAX_VALUE
                    ||skus.adjust(sku.getId(),item.getQuantity())!=1)
                throw new BusinessException(409,"退款库存恢复失败，请核对规格及库存上限");
            if(products.reduceSales(item.getProductId(),item.getQuantity())!=1)
                throw new BusinessException(409,"历史销量不足以冲减，请核对订单与销量");
            ledger.record(sku,item.getQuantity(),StockMovementKind.REFUND_RELEASE,order.getId(),
                    "refund:"+order.getId()+":"+item.getId(),operator,"ADMIN");
        }
        row.setStatus(AfterSaleStatus.REFUNDED);row.setRefundedOrderId(order.getId());sales.saveReview(row);
        cache.evict();return sales.selectById(id);
    }
}
