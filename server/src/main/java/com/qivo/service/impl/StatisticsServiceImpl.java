package com.qivo.service.impl;

import com.qivo.mapper.StatisticsMapper;
import com.qivo.service.StatisticsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;

    public StatisticsServiceImpl(StatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    @Override
    public Map<String, Object> dashboard() {
        return statisticsMapper.dashboard();
    }

    @Override
    public Map<String, Object> sales(String type) {
        LocalDate now = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        if ("week".equals(type)) {
            result.put(
                    "list",
                    statisticsMapper.salesByDay(now.minusDays(7).toString(), now.toString()));
        } else {
            result.put("list", statisticsMapper.salesByMonth(String.valueOf(now.getYear())));
        }
        return result;
    }

    @Override
    public Map<String, Object> userStats() {
        LocalDate now = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "list",
                statisticsMapper.userRegByDay(now.minusDays(30).toString(), now.toString()));
        return result;
    }

    @Override
    public Map<String, Object> revenue(String type) {
        LocalDate now = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        if ("day".equals(type)) {
            // 按天：近30天
            result.put(
                    "list",
                    statisticsMapper.salesByDay(
                            now.minusDays(30).toString(), now.plusDays(1).toString()));
        } else if ("week".equals(type)) {
            // 按天：近7天
            result.put(
                    "list",
                    statisticsMapper.salesByDay(
                            now.minusDays(7).toString(), now.plusDays(1).toString()));
        } else {
            // 按月
            result.put("list", statisticsMapper.revenueByMonth(String.valueOf(now.getYear())));
        }
        return result;
    }

    @Override
    public Map<String, Object> workbench() {
        return statisticsMapper.workbench();
    }

    @Override
    public Map<String, Object> orderStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalOrders", statisticsMapper.countTotalOrders());
        result.put("validOrders", statisticsMapper.countValidOrders());
        return result;
    }

    @Override
    public Map<String, Object> productTop10() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", statisticsMapper.productSalesTop10());
        return result;
    }
}
