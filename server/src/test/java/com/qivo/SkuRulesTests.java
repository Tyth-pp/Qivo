package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.constant.SkuConstant;
import com.qivo.dto.SkuDraftRequest;
import com.qivo.dto.SkuSelection;
import com.qivo.dto.SkuSpecificationDefinition;
import com.qivo.dto.SkuSpecificationValue;
import com.qivo.enums.ProductSpecMode;
import com.qivo.enums.SkuStatus;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.migration.SkuMigrationPreflight;
import com.qivo.service.impl.ProductSkuServiceImpl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SkuRulesTests {
    @Test
    void mysqlVersionMustActuallyEnforceCheckConstraintsIncludingForEmptySchemas()
            throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        for (String version : List.of("5.7.44", "8.0.15", "10.6.0-MariaDB")) {
            when(metadata.getDatabaseProductVersion()).thenReturn(version);
            assertThrows(
                    IllegalStateException.class,
                    () -> SkuMigrationPreflight.validateServer(connection));
        }
        for (String version : List.of("8.0.16", "8.0.39", "8.4.0")) {
            when(metadata.getDatabaseProductVersion()).thenReturn(version);
            assertDoesNotThrow(() -> SkuMigrationPreflight.validateServer(connection));
        }
    }

    private final ProductSkuMapper mapper = mock(ProductSkuMapper.class);
    private final ProductSkuServiceImpl service = new ProductSkuServiceImpl(mapper);
    private final List<SkuSpecificationDefinition> definitions =
            List.of(
                    dimension("color", "颜色", "black", "黑色", "white", "白色"),
                    dimension("capacity", "容量", "small", "小号", "large", "大号"));

    @Test
    void singleProductRequiresExactlyOneDefaultWithoutSelections() {
        assertEquals(
                List.of(SkuConstant.DEFAULT_SPEC_KEY),
                service.validateSkus(ProductSpecMode.SINGLE, List.of(), List.of(sku(List.of()))));
        assertThrows(
                BusinessException.class,
                () ->
                        service.validateSkus(
                                ProductSpecMode.SINGLE, definitions, List.of(sku(List.of()))));
        assertThrows(
                BusinessException.class,
                () ->
                        service.validateSkus(
                                ProductSpecMode.SINGLE,
                                List.of(),
                                List.of(sku(List.of()), sku(List.of()))));
        assertThrows(
                BusinessException.class,
                () ->
                        service.validateSkus(
                                ProductSpecMode.SINGLE,
                                List.of(),
                                List.of(sku(List.of(new SkuSelection("color", "black"))))));
    }

    @Test
    void orderingDoesNotChangeCombinationAndDuplicateCombinationIsRejected() {
        var a =
                sku(
                        List.of(
                                new SkuSelection("color", "black"),
                                new SkuSelection("capacity", "small")));
        var b =
                sku(
                        List.of(
                                new SkuSelection("capacity", "small"),
                                new SkuSelection("color", "black")));
        assertEquals(
                List.of("capacity=small|color=black"),
                service.validateSkus(ProductSpecMode.MULTI, definitions, List.of(a)));
        assertEquals(
                service.validateSkus(ProductSpecMode.MULTI, definitions, List.of(a)),
                service.validateSkus(ProductSpecMode.MULTI, definitions, List.of(b)));
        assertThrows(
                BusinessException.class,
                () -> service.validateSkus(ProductSpecMode.MULTI, definitions, List.of(a, b)));
    }

    @Test
    void unknownMissingRepeatedAndCrossDimensionValuesAreRejected() {
        List<List<SkuSelection>> invalid =
                List.of(
                        List.of(new SkuSelection("color", "black")),
                        List.of(
                                new SkuSelection("color", "black"),
                                new SkuSelection("color", "white")),
                        List.of(
                                new SkuSelection("color", "small"),
                                new SkuSelection("capacity", "small")),
                        List.of(
                                new SkuSelection("alien", "black"),
                                new SkuSelection("capacity", "small")));
        for (var selections : invalid)
            assertThrows(
                    BusinessException.class,
                    () ->
                            service.validateSkus(
                                    ProductSpecMode.MULTI, definitions, List.of(sku(selections))));
        assertThrows(
                BusinessException.class,
                () ->
                        service.validateSkus(
                                ProductSpecMode.MULTI,
                                definitions,
                                List.of(sku(Collections.singletonList(null)))));
    }

    @Test
    void malformedAndAmbiguousDefinitionsAreRejected() {
        var rows = List.of(sku(List.of(new SkuSelection("color", "black"))));
        List<List<SkuSpecificationDefinition>> invalid =
                List.of(
                        List.of(),
                        List.of(dimension("color|size", "颜色", "black", "黑色")),
                        List.of(dimension("Color", "颜色", "black", "黑色")),
                        List.of(
                                dimension("color", "颜色", "black", "黑色"),
                                dimension("color", "大小", "large", "大号")),
                        List.of(
                                dimension("color", "颜色", "black", "黑色"),
                                dimension("size", " 颜色 ", "large", "大号")),
                        List.of(dimension("color", "颜色", "black", "黑色", "white", " 黑色 ")),
                        List.of(dimension("color", "颜色", "black", "黑色", "black", "白色")),
                        List.of(dimension("color", "名".repeat(31), "black", "黑色")));
        for (var defs : invalid)
            assertThrows(
                    BusinessException.class,
                    () -> service.validateSkus(ProductSpecMode.MULTI, defs, rows));
        assertThrows(BusinessException.class, () -> service.validateSkus(null, List.of(), rows));
        assertThrows(
                BusinessException.class,
                () ->
                        service.validateSkus(
                                ProductSpecMode.SINGLE,
                                List.of(),
                                Collections.singletonList(null)));
    }

    @Test
    void pricePrecisionRangeOriginalPriceAndStockAreValidatedWithoutRounding() {
        for (String value : List.of("0", "-1", "0.001", "100000000.00")) {
            assertThrows(
                    BusinessException.class,
                    () ->
                            validateSingle(
                                    new SkuDraftRequest(
                                            List.of(),
                                            new BigDecimal(value),
                                            null,
                                            0,
                                            SkuStatus.ENABLED,
                                            null)));
        }
        assertThrows(
                BusinessException.class,
                () ->
                        validateSingle(
                                new SkuDraftRequest(
                                        List.of(),
                                        BigDecimal.TEN,
                                        BigDecimal.ONE,
                                        1,
                                        SkuStatus.ENABLED,
                                        null)));
        assertThrows(
                BusinessException.class,
                () ->
                        validateSingle(
                                new SkuDraftRequest(
                                        List.of(),
                                        BigDecimal.TEN,
                                        null,
                                        -1,
                                        SkuStatus.ENABLED,
                                        null)));
        assertThrows(
                BusinessException.class,
                () ->
                        validateSingle(
                                new SkuDraftRequest(
                                        List.of(), BigDecimal.TEN, null, 1, null, null)));
        assertThrows(
                BusinessException.class,
                () ->
                        validateSingle(
                                new SkuDraftRequest(
                                        List.of(),
                                        BigDecimal.TEN,
                                        null,
                                        1,
                                        SkuStatus.ENABLED,
                                        "a".repeat(256))));
        assertDoesNotThrow(
                () ->
                        validateSingle(
                                new SkuDraftRequest(
                                        List.of(),
                                        new BigDecimal("1.230"),
                                        new BigDecimal("1.23"),
                                        0,
                                        SkuStatus.DISABLED,
                                        null)));
    }

    @Test
    void boundedLargeCombinationSetIsAcceptedWithoutRequiringFullCartesianProduct() {
        List<SkuSpecificationValue> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) values.add(new SkuSpecificationValue("v" + i, "值" + i));
        var defs =
                List.of(
                        new SkuSpecificationDefinition("x", "维度一", values),
                        new SkuSpecificationDefinition("y", "维度二", values));
        List<SkuDraftRequest> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++)
            rows.add(
                    sku(
                            List.of(
                                    new SkuSelection("x", "v" + i / 10),
                                    new SkuSelection("y", "v" + i % 10))));
        assertEquals(100, service.validateSkus(ProductSpecMode.MULTI, defs, rows).size());
        assertEquals(
                1, service.validateSkus(ProductSpecMode.MULTI, defs, rows.subList(0, 1)).size());
        rows.add(rows.get(0));
        assertThrows(
                BusinessException.class,
                () -> service.validateSkus(ProductSpecMode.MULTI, defs, rows));
    }

    @Test
    void readModelRejectsUnsafeSortingAndInvalidPaginationBeforeQuerying() {
        assertThrows(
                BusinessException.class,
                () -> service.page(1, 10, null, null, 1, "price; DROP TABLE product"));
        assertThrows(BusinessException.class, () -> service.page(1, 101, null, null, 1, null));
        assertThrows(BusinessException.class, () -> service.page(0, 10, null, null, 1, null));
        assertThrows(BusinessException.class, () -> service.page(1, 10, null, null, 2, null));
        verifyNoInteractions(mapper);
    }

    private void validateSingle(SkuDraftRequest row) {
        service.validateSkus(ProductSpecMode.SINGLE, List.of(), List.of(row));
    }

    private static SkuDraftRequest sku(List<SkuSelection> choices) {
        return new SkuDraftRequest(choices, BigDecimal.TEN, null, 1, SkuStatus.ENABLED, null);
    }

    private static SkuSpecificationDefinition dimension(String id, String name, String... values) {
        List<SkuSpecificationValue> result = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2)
            result.add(new SkuSpecificationValue(values[i], values[i + 1]));
        return new SkuSpecificationDefinition(id, name, result);
    }
}
