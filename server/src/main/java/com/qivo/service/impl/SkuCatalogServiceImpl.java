package com.qivo.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.cache.HomepageCache;
import com.qivo.constant.SkuMessageConstant;
import com.qivo.dto.*;
import com.qivo.entity.*;
import com.qivo.enums.*;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.*;
import com.qivo.result.ErrorCode;
import com.qivo.service.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SkuCatalogServiceImpl implements SkuCatalogService {
    private final StockLedgerService ledger;
    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final ProductCategoryMapper categories;
    private final ProductSkuService rules;
    private final ObjectMapper json;
    private final HomepageCache cache;

    public SkuCatalogServiceImpl(
            ProductMapper products,
            ProductSkuMapper skus,
            ProductCategoryMapper categories,
            ProductSkuService rules,
            ObjectMapper json,
            HomepageCache cache,
            StockLedgerService ledger) {
        this.ledger = ledger;
        this.products = products;
        this.skus = skus;
        this.categories = categories;
        this.rules = rules;
        this.json = json;
        this.cache = cache;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(400, message);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("规格序列化失败", e);
        }
    }

    private List<SkuSpecificationDefinition> definitions(Product product) {
        try {
            return json.readValue(product.getSpecDefinition(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("商品规格数据损坏", e);
        }
    }

    private List<SkuSelection> selections(ProductSku sku) {
        try {
            return json.readValue(sku.getSpecValues(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("SKU 规格数据损坏", e);
        }
    }

    @Override
    @Transactional
    public void save(ProductSaveRequest request, boolean creating) {
        if (request == null
                || request.name() == null
                || request.name().isBlank()
                || request.name().length() > 100
                || request.categoryId() == null
                || categories.selectAll().stream()
                        .noneMatch(c -> c.getId().equals(request.categoryId()))
                || request.mainImage() == null
                || request.mainImage().length() > 255
                || request.specDefinitions() == null
                || request.skus() == null) throw invalid("请检查商品名称、分类、主图和规格");
        Product product;
        List<ProductSku> existing;
        if (creating) {
            if (request.id() != null) throw invalid("新增商品不能指定 ID");
            product = new Product();
            existing = List.of();
        } else {
            product = products.lockById(request.id());
            if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
            existing = skus.listByProduct(product.getId());
            if (product.getSpecMode() != request.specMode()) throw invalid("已有商品不支持切换规格模式，请新建商品");
            List<SkuSpecificationDefinition> old = definitions(product);
            if (old.size() != request.specDefinitions().size()) throw invalid("已有规格维度不能删除或替换");
            for (var d : old) {
                var next =
                        request.specDefinitions().stream()
                                .filter(n -> n != null && d.id().equals(n.id()))
                                .findFirst()
                                .orElseThrow(() -> invalid("已有规格维度不能替换"));
                for (var v : d.values())
                    if (next.values() == null
                            || next.values().stream()
                                    .noneMatch(n -> n != null && v.id().equals(n.id())))
                        throw invalid("已有规格值不能删除或替换");
            }
        }
        List<SkuDraftRequest> all = new ArrayList<>();
        for (var sku : existing)
            all.add(
                    new SkuDraftRequest(
                            selections(sku),
                            sku.getPrice(),
                            sku.getOriginalPrice(),
                            sku.getStock(),
                            sku.getStatus(),
                            sku.getImage()));
        all.addAll(request.skus());
        var keys = rules.validateSkus(request.specMode(), request.specDefinitions(), all);
        if ((creating || Integer.valueOf(1).equals(product.getStatus()))
                && all.stream().noneMatch(s -> s.status() == SkuStatus.ENABLED))
            throw invalid("至少保留一个启用的 SKU");
        // Client identifiers are request-local aliases. Persist server-generated IDs for new
        // dimensions/values.
        var stable = stabilize(request, creating ? List.of() : definitions(product));
        List<SkuDraftRequest> normalized = new ArrayList<>();
        for (var sku : existing)
            normalized.add(
                    new SkuDraftRequest(
                            selections(sku),
                            sku.getPrice(),
                            sku.getOriginalPrice(),
                            sku.getStock(),
                            sku.getStatus(),
                            sku.getImage()));
        normalized.addAll(stable.skus());
        keys = rules.validateSkus(stable.specMode(), stable.specDefinitions(), normalized);
        product.setName(request.name().strip());
        product.setDescription(request.description());
        product.setCategoryId(request.categoryId());
        product.setMainImage(request.mainImage());
        product.setSpecMode(request.specMode());
        product.setSpecDefinition(encode(stable.specDefinitions()));
        if (creating) {
            product.setStatus(0);
            product.setPrice(all.get(0).price());
            product.setStock(0);
            product.setSales(0);
            products.insert(product);
        } else products.updateProduct(product);
        for (int i = 0; i < request.skus().size(); i++) {
            var draft = stable.skus().get(i);
            var sku = new ProductSku();
            sku.setProductId(product.getId());
            sku.setSkuCode("QIVO-" + UUID.randomUUID().toString().replace("-", ""));
            sku.setSpecKey(keys.get(existing.size() + i));
            sku.setSpecValues(encode(draft.specValues()));
            sku.setPrice(draft.price());
            sku.setOriginalPrice(draft.originalPrice());
            sku.setStock(draft.stock());
            sku.setStatus(draft.status());
            sku.setImage(draft.image());
            skus.insert(sku);
            var initial = new ProductSku();
            initial.setId(sku.getId());
            initial.setProductId(sku.getProductId());
            initial.setStock(0);
            ledger.record(
                    initial,
                    sku.getStock(),
                    StockMovementKind.INITIAL,
                    null,
                    "initial:" + sku.getId(),
                    null,
                    "SYSTEM");
        }
        cache.evict();
    }

    private ProductSaveRequest stabilize(
            ProductSaveRequest request, List<SkuSpecificationDefinition> old) {
        Map<String, String> dimensionIds = new HashMap<>();
        Map<String, String> valueIds = new HashMap<>();
        var definitions = new ArrayList<SkuSpecificationDefinition>();
        for (var d : request.specDefinitions()) {
            var previous = old.stream().filter(v -> v.id().equals(d.id())).findFirst();
            String id =
                    previous.isPresent()
                            ? d.id()
                            : "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            dimensionIds.put(d.id(), id);
            var values = new ArrayList<SkuSpecificationValue>();
            for (var v : d.values()) {
                boolean kept =
                        previous.isPresent()
                                && previous.get().values().stream()
                                        .anyMatch(x -> x.id().equals(v.id()));
                String valueId =
                        kept
                                ? v.id()
                                : "v"
                                        + UUID.randomUUID()
                                                .toString()
                                                .replace("-", "")
                                                .substring(0, 24);
                valueIds.put(d.id() + "/" + v.id(), valueId);
                values.add(new SkuSpecificationValue(valueId, v.name().strip()));
            }
            definitions.add(new SkuSpecificationDefinition(id, d.name().strip(), values));
        }
        var drafts =
                request.skus().stream()
                        .map(
                                s ->
                                        new SkuDraftRequest(
                                                s.specValues().stream()
                                                        .map(
                                                                v ->
                                                                        new SkuSelection(
                                                                                dimensionIds.get(
                                                                                        v.specId()),
                                                                                valueIds.get(
                                                                                        v.specId()
                                                                                                + "/"
                                                                                                + v
                                                                                                        .valueId())))
                                                        .toList(),
                                                s.price(),
                                                s.originalPrice(),
                                                s.stock(),
                                                s.status(),
                                                s.image()))
                        .toList();
        return new ProductSaveRequest(
                request.id(),
                request.name(),
                request.description(),
                request.categoryId(),
                request.mainImage(),
                request.specMode(),
                definitions,
                drafts);
    }

    @Override
    @Transactional
    public void updateSku(Long productId, Long skuId, SkuUpdateRequest request) {
        Product product = products.lockById(productId);
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        var sku = skus.lockById(skuId);
        if (sku == null || !productId.equals(sku.getProductId()) || request == null)
            throw invalid(SkuMessageConstant.WRONG_PRODUCT);
        var rows = skus.listByProduct(productId);
        List<SkuDraftRequest> drafts = new ArrayList<>();
        for (var s : rows)
            drafts.add(
                    s.getId().equals(skuId)
                            ? new SkuDraftRequest(
                                    selections(s),
                                    request.price(),
                                    request.originalPrice(),
                                    s.getStock(),
                                    request.status(),
                                    request.image())
                            : new SkuDraftRequest(
                                    selections(s),
                                    s.getPrice(),
                                    s.getOriginalPrice(),
                                    s.getStock(),
                                    s.getStatus(),
                                    s.getImage()));
        rules.validateSkus(product.getSpecMode(), definitions(product), drafts);
        if (product.getStatus() == 1
                && drafts.stream().noneMatch(d -> d.status() == SkuStatus.ENABLED))
            throw invalid("请先下架商品，再禁用最后一个 SKU");
        sku.setPrice(request.price());
        sku.setOriginalPrice(request.originalPrice());
        sku.setStatus(request.status());
        sku.setImage(request.image());
        skus.updateDetails(sku);
        cache.evict();
    }

    @Override
    @Transactional
    public void adjustStock(Long productId, Long skuId, long delta) {
        adjustStock(productId, skuId, delta, null);
    }

    @Override
    @Transactional
    public void adjustStock(Long productId, Long skuId, long delta, Long adminId) {
        if (delta == 0 || delta < -2147483647L || delta > 2147483647L) throw invalid("库存增量超出范围");
        if (products.lockById(productId) == null)
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        var sku = skus.lockById(skuId);
        if (sku == null || !productId.equals(sku.getProductId()))
            throw invalid(SkuMessageConstant.WRONG_PRODUCT);
        if (skus.adjust(skuId, delta) != 1) throw invalid("库存不足或调整后超过整数上限");
        ledger.record(
                sku,
                delta,
                StockMovementKind.MANUAL_ADJUST,
                null,
                "adjust:" + UUID.randomUUID(),
                adminId,
                adminId == null ? "SYSTEM" : "ADMIN");
        cache.evict();
    }

    @Override
    public void hydrate(Product product) {
        var rows = skus.listByProduct(product.getId());
        product.setSkus(rows);
        product.setSpecDefinitions(definitions(product));
        var enabled = rows.stream().filter(s -> s.getStatus() == SkuStatus.ENABLED).toList();
        product.setPrice(
                enabled.stream().map(ProductSku::getPrice).min(BigDecimal::compareTo).orElse(null));
        product.setMaxPrice(
                enabled.stream().map(ProductSku::getPrice).max(BigDecimal::compareTo).orElse(null));
        product.setTotalStock(enabled.stream().mapToLong(ProductSku::getStock).sum());
        product.setStock((int) Math.min(Integer.MAX_VALUE, product.getTotalStock()));
        product.setOriginalPrice(
                product.getSpecMode() == ProductSpecMode.SINGLE && !enabled.isEmpty()
                        ? enabled.get(0).getOriginalPrice()
                        : null);
        product.setDefaultSkuId(
                product.getSpecMode() == ProductSpecMode.SINGLE && !enabled.isEmpty()
                        ? enabled.get(0).getId()
                        : null);
    }

    @Override
    public ProductSku lockSellable(Long skuId, int quantity) {
        if (skuId == null || quantity < 1 || quantity > 999)
            throw invalid(SkuMessageConstant.INVALID_QUANTITY);
        var ref = skus.selectById(skuId);
        if (ref == null) throw invalid(SkuMessageConstant.NOT_FOUND);
        var product = products.lockById(ref.getProductId());
        var sku = skus.lockById(skuId);
        if (product == null || product.getStatus() != 1 || sku.getStatus() != SkuStatus.ENABLED)
            throw invalid(SkuMessageConstant.UNAVAILABLE);
        if (sku.getStock() < quantity)
            throw new BusinessException(ErrorCode.PRODUCT_STOCK_INSUFFICIENT);
        return sku;
    }

    @Override
    public String specText(Product product, ProductSku sku) {
        var labels = new ArrayList<String>();
        for (var choice : selections(sku)) {
            var d =
                    definitions(product).stream()
                            .filter(v -> v.id().equals(choice.specId()))
                            .findFirst()
                            .orElseThrow();
            var v =
                    d.values().stream()
                            .filter(value -> value.id().equals(choice.valueId()))
                            .findFirst()
                            .orElseThrow();
            labels.add(d.name() + "：" + v.name());
        }
        return labels.isEmpty() ? "默认规格" : String.join(" / ", labels);
    }

    @Override
    public String snapshot(ProductSku sku) {
        return encode(
                Map.of(
                        "text",
                        specText(products.selectById(sku.getProductId()), sku),
                        "selections",
                        selections(sku)));
    }
}
