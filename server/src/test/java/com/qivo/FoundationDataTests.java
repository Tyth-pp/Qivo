package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.cache.HomepageCache;
import com.qivo.development.DevelopmentDataInitializer;
import com.qivo.entity.Product;
import com.qivo.entity.ProductSku;
import com.qivo.enums.SkuStatus;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.ProductMapper;
import com.qivo.mapper.ProductSkuMapper;
import com.qivo.service.FileStorageService;
import com.qivo.service.OrderService;
import com.qivo.service.ProductService;
import com.qivo.service.impl.LocalFileStorageServiceImpl;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FoundationDataTests {
    @Test
    void developmentInitializationRejectsHistorySystemAndUnconfirmedDatabases() throws Exception {
        Connection connection = mock(Connection.class);
        for (String database :
                List.of(
                        "mall",
                        "MALL",
                        "mysql",
                        "information_schema",
                        "performance_schema",
                        "sys")) {
            when(connection.getCatalog()).thenReturn(database);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> DevelopmentDataInitializer.initialize(connection, database, Map.of()));
        }
        when(connection.getCatalog()).thenReturn("qivo");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DevelopmentDataInitializer.initialize(
                                connection, "other_database", Map.of()));
        verify(connection, never()).getAutoCommit();
        verify(connection, never()).prepareStatement(anyString());
    }

    @Autowired DataSource dataSource;
    @Autowired ProductMapper products;
    @Autowired ProductSkuMapper skus;
    @Autowired ProductService service;
    @Autowired FileStorageService fileStorage;
    @Autowired OrderService orders;
    @Autowired HomepageCache homepageCache;

    @Test
    void refactoredCollaboratorsAreWiredAndTransactionProxyIsPreserved() {
        assertInstanceOf(LocalFileStorageServiceImpl.class, fileStorage);
        assertNotNull(homepageCache);
        assertTrue(AopUtils.isAopProxy(orders));
    }

    @Test
    void hikariAndGeneratedCrudAndXmlPaginationWorkTogether() {
        assertInstanceOf(HikariDataSource.class, dataSource);
        for (int i = 0; i < 3; i++) {
            Product p = new Product();
            p.setName("分页基线" + i);
            p.setPrice(new BigDecimal("12.50"));
            p.setStock(10);
            p.setStatus(1);
            p.setCategoryId(1L);
            assertEquals(1, products.insert(p));
            assertNotNull(p.getId());
            ProductSku sku = new ProductSku();
            sku.setProductId(p.getId());
            sku.setSkuCode("PAGE-" + p.getId());
            sku.setSpecKey("DEFAULT");
            sku.setSpecValues("[]");
            sku.setPrice(p.getPrice());
            sku.setStock(p.getStock());
            sku.setStatus(SkuStatus.ENABLED);
            skus.insert(sku);
            assertEquals(p.getName(), products.selectById(p.getId()).getName());
        }
        var page = service.page(1, 2, "分页基线", null, 1, null);
        assertEquals(3, page.getTotal());
        assertEquals(2, page.getRecords().size());
        var last = service.page(2, 2, "分页基线", null, 1, null);
        assertEquals(1, last.getRecords().size());
        assertTrue(service.page(3, 2, "分页基线", null, 1, null).getRecords().isEmpty());
    }

    @Test
    void paginationRejectsInvalidLimits() {
        assertThrows(BusinessException.class, () -> service.page(0, 20, null, null, null, null));
        assertThrows(BusinessException.class, () -> service.page(1, 101, null, null, null, null));
    }
}
