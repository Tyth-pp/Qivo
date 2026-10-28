package com.qivo.controller.admin;

import com.qivo.service.StatisticsService;
import com.qivo.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "后台-数据统计")
@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "仪表盘概览数据")
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statisticsService.dashboard());
    }

    @Operation(summary = "销量统计")
    @GetMapping("/sales")
    public Result<Map<String, Object>> sales(@RequestParam(defaultValue = "week") String type) {
        return Result.success(statisticsService.sales(type));
    }

    @Operation(summary = "用户统计")
    @GetMapping("/user")
    public Result<Map<String, Object>> userStats() {
        return Result.success(statisticsService.userStats());
    }

    @Operation(summary = "营收统计")
    @GetMapping("/revenue")
    public Result<Map<String, Object>> revenue(@RequestParam(defaultValue = "month") String type) {
        return Result.success(statisticsService.revenue(type));
    }

    @Operation(summary = "工作台数据")
    @GetMapping("/workbench")
    public Result<Map<String, Object>> workbench() {
        return Result.success(statisticsService.workbench());
    }

    @Operation(summary = "订单统计")
    @GetMapping("/orderStats")
    public Result<Map<String, Object>> orderStats() {
        return Result.success(statisticsService.orderStats());
    }

    @Operation(summary = "商品销量TOP10")
    @GetMapping("/productTop10")
    public Result<Map<String, Object>> productTop10() {
        return Result.success(statisticsService.productTop10());
    }
}
