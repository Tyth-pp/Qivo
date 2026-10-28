package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.OrderItemMapper;
import com.qivo.mapper.OrderMapper;
import com.qivo.result.ErrorCode;
import com.qivo.service.impl.OrderServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AdminOrderDetailTests {
    @Mock OrderMapper orders;
    @Mock OrderItemMapper items;
    // 其余依赖不注入；详情不得访问当前商品、规格、地址或缓存补造数据。
    @InjectMocks OrderServiceImpl service;

    @Test
    void usesStoredSnapshotsAndPreservesAdjustedOrderAmount() {
        var order = new Order();
        order.setId(1L);
        order.setOrderNo("SNAPSHOT-1");
        order.setTotalAmount(new BigDecimal("80.00"));
        order.setReceiverAddressSnapshot("下单时地址");
        var item = new OrderItem();
        item.setProductName("下单时商品名称");
        item.setProductImg("/upload/historical.png");
        item.setSkuSpecSnapshot("{\"text\":\"暖白 / 480ml\"}");
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setQuantity(2);
        item.setTotalPrice(new BigDecimal("100.00"));
        when(orders.selectById(1L)).thenReturn(order);
        when(items.selectByOrderId(1L)).thenReturn(List.of(item));

        var detail = service.getAdminDetail(1L);

        assertEquals("SNAPSHOT-1", detail.getOrderNo());
        assertEquals(order.getTotalAmount(), detail.getTotalAmount());
        assertEquals("下单时地址", detail.getReceiverAddressSnapshot());
        assertEquals("下单时商品名称", detail.getItems().get(0).getProductName());
        assertEquals("/upload/historical.png", detail.getItems().get(0).getProductImg());
        assertEquals(item.getSkuSpecSnapshot(), detail.getItems().get(0).getSkuSpecSnapshot());
        assertEquals(new BigDecimal("100.00"), detail.getItems().get(0).getTotalPrice());
    }

    @Test
    void missingLegacySnapshotsAndEmptyItemsStayMissing() {
        var order = new Order();
        order.setId(2L);
        order.setAddressSnapshotSource("LEGACY");
        var item = new OrderItem();
        item.setSkuSnapshotSource("LEGACY");
        when(orders.selectById(2L)).thenReturn(order);
        when(items.selectByOrderId(2L)).thenReturn(List.of(item), List.of());
        var detail = service.getAdminDetail(2L);
        assertNull(detail.getReceiverAddressSnapshot());
        assertNull(detail.getItems().get(0).getSkuSpecSnapshot());
        assertNull(detail.getItems().get(0).getSkuId());
        assertTrue(service.getAdminDetail(2L).getItems().isEmpty());
    }

    @Test
    void absentOrderFailsBeforeQueryingItems() {
        when(orders.selectById(404L)).thenReturn(null);
        var failure = assertThrows(BusinessException.class, () -> service.getAdminDetail(404L));
        assertEquals(ErrorCode.ORDER_NOT_EXIST.getCode(), failure.getCode());
        verifyNoInteractions(items);
    }
}
