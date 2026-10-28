package com.qivo.controller.fore;
import com.qivo.dto.AfterSaleApplyRequest;
import com.qivo.result.Result;
import com.qivo.security.AccountPrincipal;
import com.qivo.service.AfterSaleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/fore/after-sale")
public class AfterSaleController {
    private final AfterSaleService service;
    public AfterSaleController(AfterSaleService service){this.service=service;}
    @PostMapping
    public Result<?> apply(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader("Idempotency-Key") @Pattern(regexp="[a-zA-Z0-9_-]{8,64}") String key,
            @Valid @RequestBody AfterSaleApplyRequest request) {
        return Result.success(service.apply(principal.id(),request.orderId(),key,request.reason()));
    }
    @GetMapping("/order/{orderId}")
    public Result<?> history(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable Long orderId) {
        return Result.success(service.history(principal.id(),orderId));
    }
}
