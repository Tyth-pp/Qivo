package com.qivo.mapper;

import com.qivo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface OrderItemMapper {
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    int insert(OrderItem item);

    int batchInsert(@Param("list") List<OrderItem> items);
}
