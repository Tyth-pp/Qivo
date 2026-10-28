package com.qivo.service;

import com.qivo.dto.SkuDraftRequest;
import com.qivo.dto.SkuSpecificationDefinition;
import com.qivo.enums.ProductSpecMode;
import com.qivo.vo.PageVO;
import com.qivo.vo.SkuProductSummaryVO;
import java.util.List;

/** A 批提供规则校验和隔离读模型，无 Controller 和销售写入口。 */
public interface ProductSkuService {
    /** 返回与输入 SKU 顺序一致的规范组合键。 */
    List<String> validateSkus(
            ProductSpecMode mode,
            List<SkuSpecificationDefinition> definitions,
            List<SkuDraftRequest> skus);

    PageVO<SkuProductSummaryVO> page(
            int page, int pageSize, String keyword, Long categoryId, Integer status, String sort);
}
