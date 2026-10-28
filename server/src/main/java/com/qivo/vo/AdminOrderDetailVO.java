package com.qivo.vo;

import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;

import org.springframework.beans.BeanUtils;

import java.util.List;

/** 保留既有订单字段，附加下单时已保存的明细，不关联当前商品或地址补造快照。 */
public class AdminOrderDetailVO extends Order {
    private final List<OrderItem> items;

    public AdminOrderDetailVO(Order order, List<OrderItem> items) {
        BeanUtils.copyProperties(order, this);
        this.items = List.copyOf(items);
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
