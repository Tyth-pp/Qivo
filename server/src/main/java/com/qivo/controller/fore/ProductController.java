package com.qivo.controller.fore;

import com.qivo.constant.SecurityConstant;
import com.qivo.entity.Product;
import com.qivo.enums.SkuStatus;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import com.qivo.service.ProductService;
import com.qivo.vo.PageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "前台-商品")
@RestController
@RequestMapping("/api/fore/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "商品列表")
    @GetMapping("/list")
    public Result<PageVO<Product>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        return Result.success(productService.page(page, pageSize, keyword, categoryId, 1, sort));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        Product product = productService.getById(id);
        if (!Integer.valueOf(1).equals(product.getStatus()))
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EXIST);
        Map<String, Object> result = new HashMap<>();
        result.put("product", product);
        result.put("specDefinitions", product.getSpecDefinitions());
        result.put(
                "skus",
                product.getSkus().stream()
                        .filter(s -> s.getStatus() == SkuStatus.ENABLED)
                        .toList());
        result.put("defaultSkuId", product.getDefaultSkuId());
        product.setSkus(null);
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        result.put("collected", userId != null && productService.isCollected(userId, id));
        return Result.success(result);
    }

    @Operation(summary = "收藏商品")
    @PostMapping("/collect")
    public Result<Void> collect(HttpServletRequest request, @RequestBody Map<String, Long> params) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        productService.collect(userId, params.get("productId"));
        return Result.success();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/collect/{productId}")
    public Result<Void> cancelCollect(@PathVariable Long productId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        productService.cancelCollect(userId, productId);
        return Result.success();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/collect")
    public Result<List<Long>> collectList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        return Result.success(productService.collectList(userId));
    }
}
