package com.qivo.service.impl;

import com.qivo.constant.SkuConstant;
import com.qivo.constant.SkuMessageConstant;
import com.qivo.dto.SkuDraftRequest;
import com.qivo.dto.SkuSelection;
import com.qivo.dto.SkuSpecificationDefinition;
import com.qivo.dto.SkuSpecificationValue;
import com.qivo.enums.ProductSpecMode;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.service.ProductSkuService;
import com.qivo.vo.PageVO;
import com.qivo.vo.SkuProductSummaryVO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProductSkuServiceImpl implements ProductSkuService {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private final ProductSkuMapper skuMapper;

    public ProductSkuServiceImpl(ProductSkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Override
    public List<String> validateSkus(
            ProductSpecMode mode,
            List<SkuSpecificationDefinition> definitions,
            List<SkuDraftRequest> skus) {
        if (mode == null
                || definitions == null
                || skus == null
                || skus.isEmpty()
                || skus.size() > SkuConstant.MAX_SKUS) fail(SkuMessageConstant.INVALID_DEFINITION);
        if (mode == ProductSpecMode.SINGLE) {
            if (!definitions.isEmpty()
                    || skus.size() != 1
                    || skus.get(0) == null
                    || skus.get(0).specValues() == null
                    || !skus.get(0).specValues().isEmpty())
                fail(SkuMessageConstant.INVALID_DEFAULT);
            validatePriceStock(skus.get(0));
            return List.of(SkuConstant.DEFAULT_SPEC_KEY);
        }
        if (definitions.isEmpty() || definitions.size() > SkuConstant.MAX_DIMENSIONS)
            fail(SkuMessageConstant.INVALID_DEFINITION);
        Map<String, Set<String>> allowed = new HashMap<>();
        Set<String> dimensionNames = new HashSet<>();
        for (SkuSpecificationDefinition definition : definitions) {
            if (definition == null
                    || !validId(definition.id())
                    || !validLabel(definition.name())
                    || !dimensionNames.add(definition.name().strip())
                    || allowed.containsKey(definition.id())
                    || definition.values() == null
                    || definition.values().isEmpty()
                    || definition.values().size() > SkuConstant.MAX_VALUES_PER_DIMENSION)
                fail(SkuMessageConstant.INVALID_DEFINITION);
            Set<String> values = new HashSet<>();
            Set<String> labels = new HashSet<>();
            for (SkuSpecificationValue value : definition.values()) {
                if (value == null
                        || !validId(value.id())
                        || !validLabel(value.name())
                        || !values.add(value.id())
                        || !labels.add(value.name().strip()))
                    fail(SkuMessageConstant.INVALID_DEFINITION);
            }
            allowed.put(definition.id(), values);
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SkuDraftRequest sku : skus) {
            validatePriceStock(sku);
            if (sku.specValues() == null || sku.specValues().size() != allowed.size())
                fail(SkuMessageConstant.INVALID_COMBINATION);
            Map<String, String> selected = new TreeMap<>();
            for (SkuSelection choice : sku.specValues()) {
                if (choice == null
                        || !allowed.containsKey(choice.specId())
                        || !allowed.get(choice.specId()).contains(choice.valueId())
                        || selected.putIfAbsent(choice.specId(), choice.valueId()) != null)
                    fail(SkuMessageConstant.INVALID_COMBINATION);
            }
            String key =
                    selected.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining("|"));
            if (!seen.add(key)) fail(SkuMessageConstant.DUPLICATE_COMBINATION);
            result.add(key);
        }
        return List.copyOf(result);
    }

    @Override
    public PageVO<SkuProductSummaryVO> page(
            int page, int pageSize, String keyword, Long categoryId, Integer status, String sort) {
        if (sort != null && !Set.of("", "price_asc", "price_desc", "sales").contains(sort))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        if (status != null && status != 0 && status != 1)
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        var request = PageRequests.<SkuProductSummaryVO>of(page, pageSize);
        var records = skuMapper.selectSummaries(request, keyword, categoryId, status, sort);
        return PageVO.of(records, request.getTotal(), page, pageSize);
    }

    private static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    private static boolean validLabel(String label) {
        return label != null
                && !label.isBlank()
                && label.strip().codePointCount(0, label.strip().length())
                        <= SkuConstant.MAX_LABEL_LENGTH;
    }

    private static boolean validMoney(BigDecimal price) {
        return price != null
                && price.signum() > 0
                && price.stripTrailingZeros().scale() <= 2
                && price.compareTo(MAX_PRICE) <= 0;
    }

    private static void validatePriceStock(SkuDraftRequest sku) {
        if (sku == null
                || !validMoney(sku.price())
                || sku.stock() == null
                || sku.stock() < 0
                || sku.status() == null
                || (sku.originalPrice() != null
                        && (!validMoney(sku.originalPrice())
                                || sku.originalPrice().compareTo(sku.price()) < 0)))
            fail(SkuMessageConstant.INVALID_PRICE_STOCK);
        if (sku.image() != null && sku.image().codePointCount(0, sku.image().length()) > 255)
            fail(SkuMessageConstant.INVALID_IMAGE);
    }

    private static void fail(String message) {
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message);
    }
}
