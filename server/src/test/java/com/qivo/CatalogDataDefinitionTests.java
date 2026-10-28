package com.qivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.qivo.development.CatalogDataInitializer;
import com.qivo.dto.SkuDraftRequest;
import com.qivo.enums.ProductSpecMode;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.service.impl.ProductSkuServiceImpl;
import org.junit.jupiter.api.Test;

class CatalogDataDefinitionTests {
    @Test
    void allFortyProductsFollowTheActualSkuBusinessRules() throws Exception {
        var catalog = CatalogDataInitializer.catalog();
        var validator = new ProductSkuServiceImpl(mock(ProductSkuMapper.class));
        for (var product : catalog.products()) {
            var drafts = product.skus().stream().map(sku -> new SkuDraftRequest(
                    sku.selections(), sku.price(), null, sku.stock(), sku.status(), "/upload/products/" + product.image())).toList();
            var keys = validator.validateSkus(
                    product.definitions().isEmpty() ? ProductSpecMode.SINGLE : ProductSpecMode.MULTI,
                    product.definitions(), drafts);
            assertEquals(product.skus().size(), keys.size());
            assertFalse((product.name() + product.description()).contains("演示"));
        }
        assertEquals(15, catalog.products().stream().filter(p -> !p.definitions().isEmpty()).count());
        for (String category : new String[] {"keyboards", "computers", "drinkware", "storage", "lighting"}) {
            assertEquals(8, catalog.products().stream().filter(p -> p.category().equals(category)).count());
            assertEquals(3, catalog.products().stream().filter(p -> p.category().equals(category) && !p.definitions().isEmpty()).count());
        }
    }
}
