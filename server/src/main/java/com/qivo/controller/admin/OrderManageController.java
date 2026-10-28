package com.qivo.controller.admin;

import com.qivo.annotation.Log;
import com.qivo.entity.Order;
import com.qivo.service.OrderService;
import com.qivo.result.Result;
import com.qivo.vo.PageVO;
import com.qivo.vo.AdminOrderDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "后台-订单管理")
@RestController
@RequestMapping("/api/admin/order")
public class OrderManageController {

    private final OrderService orderService;

    public OrderManageController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "订单列表")
    @GetMapping("/list")
    public Result<PageVO<Order>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(
                orderService.page(page, pageSize, orderNo, status, startTime, endTime));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/detail/{orderId}")
    public Result<AdminOrderDetailVO> detail(@PathVariable Long orderId) {
        return Result.success(orderService.getAdminDetail(orderId));
    }

    @Operation(summary = "修改订单金额")
    @PutMapping("/price/{orderId}")
    @Log(value = "修改订单金额", type = "UPDATE")
    public Result<Void> updatePrice(
            @PathVariable Long orderId, @RequestBody Map<String, BigDecimal> params) {
        orderService.updatePrice(orderId, params.get("totalAmount"));
        return Result.success();
    }

    @Operation(summary = "发货")
    @PutMapping("/ship/{orderId}")
    @Log(value = "订单发货", type = "UPDATE")
    public Result<Void> ship(@PathVariable Long orderId, @RequestBody Map<String, String> params) {
        orderService.ship(orderId, params.get("trackingNo"));
        return Result.success();
    }

    @Operation(summary = "处理售后")
    @PutMapping("/refund/{orderId}")
    @Log(value = "处理售后", type = "UPDATE")
    public Result<Void> handleRefund(
            @PathVariable Long orderId, @RequestBody Map<String, Boolean> params) {
        orderService.handleRefund(orderId, params.getOrDefault("approve", false));
        return Result.success();
    }
}
