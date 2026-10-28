package com.qivo.controller.admin;

import com.qivo.constant.SecurityConstant;
import com.qivo.result.Result;
import com.qivo.dto.PasswordChangeRequest;
import com.qivo.dto.AdminProfileRequest;
import jakarta.validation.Valid;
import com.qivo.service.AdminService;
import com.qivo.security.AccountPrincipal;
import com.qivo.security.AdminAccessPolicy;
import com.qivo.vo.AdminAccessVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后台-管理员")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "当前管理员有效权限", description = "按当前登录身份返回菜单及操作所需权限，不接收其他账号 ID")
    @GetMapping("/access")
    public Result<AdminAccessVO> access(
            @AuthenticationPrincipal AccountPrincipal principal, Authentication authentication,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        var granted = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        return Result.success(new AdminAccessVO(
                principal.id(), principal.username(), granted.stream().sorted().toList(),
                AdminAccessPolicy.capabilities(granted)));
    }

    @Operation(summary = "管理员个人信息")
    @GetMapping("/profile")
    public Result<?> profile(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        return Result.success(adminService.getById(adminId));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody AdminProfileRequest input) {
        adminService.updateProfile(actor.id(), input.realName(), input.avatar());
        return Result.success();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(
            HttpServletRequest request, @Valid @RequestBody PasswordChangeRequest params) {
        Long adminId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        adminService.updatePassword(adminId, params.oldPassword(), params.newPassword());
        return Result.success();
    }
}
