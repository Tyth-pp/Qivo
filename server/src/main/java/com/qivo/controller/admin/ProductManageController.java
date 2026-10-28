package com.qivo.controller.admin;

import com.qivo.annotation.Log;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.ProductSaveRequest;
import com.qivo.dto.SkuStockRequest;
import com.qivo.dto.SkuUpdateRequest;
import com.qivo.entity.Product;
import com.qivo.entity.ProductCategory;
import com.qivo.entity.StockMovement;
import com.qivo.result.Result;
import com.qivo.service.ProductService;
import com.qivo.service.SkuCatalogService;
import com.qivo.service.StockLedgerService;
import com.qivo.vo.PageVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "后台-商品管理")
@RestController
@RequestMapping("/api/admin/product")
public class ProductManageController {

    private final StockLedgerService ledger;
    private final SkuCatalogService catalog;
    private final ProductService productService;

    public ProductManageController(
            ProductService productService, SkuCatalogService catalog, StockLedgerService ledger) {
        this.ledger = ledger;
        this.catalog = catalog;
        this.productService = productService;
    }

    @Operation(summary = "商品列表")
    @GetMapping("/list")
    public Result<PageVO<Product>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return Result.success(
                productService.page(page, pageSize, keyword, categoryId, status, null));
    }

    @Operation(summary = "新增商品")
    @PostMapping("/add")
    @Log(value = "新增商品", type = "INSERT")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') or hasAuthority('product:add')")
    public Result<Void> add(@RequestBody ProductSaveRequest product) {
        catalog.save(product, true);
        return Result.success();
    }

    @Operation(summary = "修改商品")
    @PutMapping("/update")
    @Log(value = "修改商品", type = "UPDATE")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') or hasAuthority('product:update')")
    public Result<Void> update(@RequestBody ProductSaveRequest product) {
        catalog.save(product, false);
        return Result.success();
    }

    @Operation(summary = "商品上架/下架")
    @PutMapping("/status/{id}")
    @Log(value = "修改商品状态", type = "UPDATE")
    public Result<Void> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, Integer> params) {
        productService.updateStatus(id, params.get("status"));
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    public Result<Product> detail(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @PutMapping("/{productId}/sku/{skuId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') or hasAuthority('product:update')")
    public Result<Void> updateSku(
            @PathVariable Long productId,
            @PathVariable Long skuId,
            @RequestBody SkuUpdateRequest request) {
        catalog.updateSku(productId, skuId, request);
        return Result.success();
    }

    @PostMapping("/{productId}/sku/{skuId}/stock")
    @PreAuthorize("hasAuthority('SUPER_ADMIN') or hasAuthority('product:update')")
    public Result<Void> stock(
            @PathVariable Long productId,
            @PathVariable Long skuId,
            @Valid @RequestBody SkuStockRequest request,
            HttpServletRequest http) {
        catalog.adjustStock(
                productId,
                skuId,
                request.delta(),
                (Long) http.getAttribute(SecurityConstant.REQUEST_USER_ID));
        return Result.success();
    }

    @GetMapping("/{productId}/stock-movements")
    public Result<PageVO<StockMovement>> stockMovements(
            @PathVariable Long productId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(ledger.page(productId, skuId, page, pageSize));
    }

    @Operation(summary = "分类列表")
    @GetMapping("/category/list")
    public Result<List<ProductCategory>> categoryList() {
        return Result.success(productService.categoryList());
    }

    @Operation(summary = "新增分类")
    @PostMapping("/category/add")
    @Log(value = "新增分类", type = "INSERT")
    public Result<Void> addCategory(@RequestBody ProductCategory category) {
        productService.addCategory(category);
        return Result.success();
    }

    @Operation(summary = "修改分类")
    @PutMapping("/category/update")
    @Log(value = "修改分类", type = "UPDATE")
    public Result<Void> updateCategory(@RequestBody ProductCategory category) {
        productService.updateCategory(category);
        return Result.success();
    }
}
