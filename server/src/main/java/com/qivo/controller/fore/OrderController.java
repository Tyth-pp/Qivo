package com.qivo.controller.fore;

import com.qivo.constant.OrderConstant;
import com.qivo.constant.OrderMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.BuyNowRequest;
import com.qivo.dto.CheckoutRequest;
import com.qivo.entity.Order;
import com.qivo.entity.OrderItem;
import com.qivo.entity.ProductComment;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.ProductCommentMapper;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import com.qivo.service.OrderService;
import com.qivo.service.OrderSubmissionService;
import com.qivo.vo.PageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "前台-订单")
@RestController
@RequestMapping("/api/fore/order")
public class OrderController {

    private final OrderSubmissionService submissions;
    private final OrderService orderService;
    private final ProductCommentMapper commentMapper;

    public OrderController(
            OrderService orderService,
            ProductCommentMapper commentMapper,
            OrderSubmissionService submissions) {
        this.submissions = submissions;
        this.orderService = orderService;
        this.commentMapper = commentMapper;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
    }

    @Operation(summary = "生成订单")
    @PostMapping("/create")
    public Result<Map<String, Object>> create(
            HttpServletRequest request,
            @RequestHeader(OrderConstant.IDEMPOTENCY_HEADER) String key,
            @Valid @RequestBody CheckoutRequest params) {
        return Result.success(submissions.checkout(getUserId(request), key, params));
    }

    @Operation(summary = "立即购买生成订单")
    @PostMapping("/buy-now")
    public Result<Map<String, Object>> buyNow(
            HttpServletRequest request,
            @RequestHeader(OrderConstant.IDEMPOTENCY_HEADER) String key,
            @Valid @RequestBody BuyNowRequest params) {
        return Result.success(submissions.buyNow(getUserId(request), key, params));
    }

    @Operation(summary = "支付订单")
    @PostMapping("/pay")
    public Result<Void> pay(HttpServletRequest request, @RequestBody Map<String, Long> params) {
        orderService.pay(params.get("orderId"), getUserId(request));
        return Result.success();
    }

    @Operation(summary = "取消订单")
    @PutMapping("/cancel/{orderId}")
    public Result<Void> cancel(@PathVariable Long orderId, HttpServletRequest request) {
        orderService.cancel(orderId, getUserId(request));
        return Result.success();
    }

    @Operation(summary = "确认收货")
    @PutMapping("/confirm/{orderId}")
    public Result<Void> confirm(@PathVariable Long orderId, HttpServletRequest request) {
        orderService.confirm(orderId, getUserId(request));
        return Result.success();
    }

    @Operation(summary = "订单列表")
    @GetMapping("/list")
    public Result<PageVO<Order>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(orderService.userPage(getUserId(request), page, pageSize, status));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/detail/{orderId}")
    public Result<Map<String, Object>> detail(
            @PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderService.getById(orderId);
        if (!order.getUserId().equals(getUserId(request)))
            throw new BusinessException(ErrorCode.NOT_FOUND);
        List<OrderItem> items = orderService.getItems(orderId);
        List<ProductComment> comments = commentMapper.selectByOrderId(orderId);
        return Result.success(Map.of("order", order, "items", items, "comments", comments));
    }

    @Operation(summary = "申请退货退款")
    @PostMapping("/refund")
    public Result<Void> refund(HttpServletRequest request, @RequestBody Map<String, Long> params) {
        throw new BusinessException(409, OrderMessageConstant.AFTERSALE_UNAVAILABLE);
    }
}
