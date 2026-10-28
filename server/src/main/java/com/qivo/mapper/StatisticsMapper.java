package com.qivo.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

@Mapper
public interface StatisticsMapper {
    // 仪表盘
    Map<String, Object> dashboard();

    List<Map<String, Object>> salesByDay(String startDate, String endDate);

    List<Map<String, Object>> salesByMonth(String year);

    List<Map<String, Object>> userRegByDay(String startDate, String endDate);

    List<Map<String, Object>> revenueByMonth(String year);

    Integer countTodayOrders();

    BigDecimal todayRevenue();

    Integer countNewUsersToday();

    Integer countTotalProducts();

    // 工作台
    Map<String, Object> workbench();

    List<Map<String, Object>> productSalesTop10();

    // 订单统计
    Long countTotalOrders();

    Long countValidOrders();

    List<Map<String, Object>> revenueStats();
}
