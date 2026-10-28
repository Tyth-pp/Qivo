package com.qivo.service;
import com.qivo.entity.AfterSale;
import com.qivo.entity.RefundAttempt;
import com.qivo.vo.PageVO;
import java.util.List;
public interface AfterSaleService {
    AfterSale apply(Long userId,Long orderId,String key,String reason);
    List<AfterSale> history(Long userId,Long orderId);
    PageVO<AfterSale> page(int page,int pageSize,String status,String orderNo);
    AfterSale review(Long id,Long operator,boolean approve,String note);
    List<RefundAttempt> attempts(Long id);
}
