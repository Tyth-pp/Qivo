package com.qivo.controller.fore;

import com.qivo.constant.SecurityConstant;
import com.qivo.dto.CartAddRequest;
import com.qivo.dto.CartCheckRequest;
import com.qivo.dto.CartQuantityRequest;
import com.qivo.result.Result;
import com.qivo.service.CartService;
import com.qivo.vo.CartVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "前台-购物车")
@RestController
@RequestMapping("/api/fore/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
    }

    @Operation(summary = "购物车列表")
    @GetMapping("/list")
    public Result<List<CartVO>> list(HttpServletRequest request) {
        return Result.success(cartService.list(getUserId(request)));
    }

    @Operation(summary = "加入购物车")
    @PostMapping("/add")
    public Result<Void> add(HttpServletRequest request, @Valid @RequestBody CartAddRequest params) {
        cartService.add(getUserId(request), params.skuId(), params.quantity());
        return Result.success();
    }

    @Operation(summary = "修改数量")
    @PutMapping("/update")
    public Result<Void> update(
            HttpServletRequest request, @Valid @RequestBody CartQuantityRequest params) {
        cartService.update(params.cartId(), getUserId(request), params.quantity());
        return Result.success();
    }

    @Operation(summary = "删除购物车项")
    @DeleteMapping("/delete/{cartId}")
    public Result<Void> delete(@PathVariable Long cartId, HttpServletRequest request) {
        cartService.delete(cartId, getUserId(request));
        return Result.success();
    }

    @Operation(summary = "勾选/取消勾选")
    @PutMapping("/check")
    public Result<Void> check(
            HttpServletRequest request, @Valid @RequestBody CartCheckRequest params) {
        cartService.check(params.cartId(), getUserId(request), params.checked());
        return Result.success();
    }

    @Operation(summary = "获取勾选项（结算用）")
    @GetMapping("/checked")
    public Result<List<CartVO>> checked(HttpServletRequest request) {
        return Result.success(cartService.getChecked(getUserId(request)));
    }
}
