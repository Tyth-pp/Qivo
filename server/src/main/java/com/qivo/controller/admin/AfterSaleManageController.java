package com.qivo.controller.admin;
import com.qivo.dto.AfterSaleReviewRequest;
import com.qivo.result.Result;
import com.qivo.security.AccountPrincipal;
import com.qivo.service.AfterSaleService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/after-sale")
public class AfterSaleManageController {
    private final AfterSaleService service;
    public AfterSaleManageController(AfterSaleService service){this.service=service;}
    @GetMapping("/list")
    public Result<?> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,
            @RequestParam(required=false) String status,@RequestParam(required=false) String orderNo) {
        return Result.success(service.page(page,pageSize,status,orderNo));
    }
    @GetMapping("/{id}/attempts")
    public Result<?> attempts(@PathVariable Long id){return Result.success(service.attempts(id));}
    @PostMapping("/{id}/review")
    public Result<?> review(@PathVariable Long id,@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody AfterSaleReviewRequest request) {
        return Result.success(service.review(id,principal.id(),request.approve(),request.note()));
    }
}
