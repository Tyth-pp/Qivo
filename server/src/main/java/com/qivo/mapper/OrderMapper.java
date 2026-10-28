package com.qivo.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qivo.entity.Order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @Select("SELECT * FROM `order` WHERE id=#{id} FOR UPDATE")
    Order lockById(@Param("id") Long id);

    @Select("SELECT CURRENT_TIMESTAMP")
    LocalDateTime databaseTime();

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    List<Order> selectPage(
            IPage<?> page,
            @Param("orderNo") String orderNo,
            @Param("status") Integer status,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    List<Order> selectByUserId(
            IPage<?> page, @Param("userId") Long userId, @Param("status") Integer status);

    List<Order> selectUnpaidBefore(@Param("expireTime") LocalDateTime expireTime);

    int insert(Order order);

    int updatePrice(@Param("id") Long id, @Param("totalAmount") BigDecimal totalAmount);

    int updatePayStatus(@Param("id") Long id, @Param("payStatus") Integer payStatus);

    int payUnpaid(@Param("id") Long id);

    int cancelUnpaid(@Param("id") Long id);

    @Update("UPDATE `order` SET pay_status=2,update_time=NOW() WHERE id=#{id} AND pay_status=1 AND ship_status=0")
    int refundPaid(@Param("id") Long id);

    int updateShipStatus(
            @Param("id") Long id,
            @Param("shipStatus") Integer shipStatus,
            @Param("trackingNo") String trackingNo);
}
