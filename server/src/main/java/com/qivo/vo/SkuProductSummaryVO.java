package com.qivo.vo;

import com.qivo.enums.ProductSpecMode;
import java.math.BigDecimal;
import lombok.Data;

/** SKU 汇总读模型；不读取 Product 的历史价格、库存列。A 批暂不接入 HTTP。 */
@Data
public class SkuProductSummaryVO {
    private Long productId;
    private String name;
    private String mainImage;
    private Long categoryId;
    private Integer status;
    private ProductSpecMode specMode;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Long totalStock;
    private Integer enabledSkuCount;
    private Long defaultSkuId;
}
