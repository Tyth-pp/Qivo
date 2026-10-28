package com.qivo.controller.common;

import com.qivo.annotation.Log;
import com.qivo.cache.RedisStore;
import com.qivo.constant.AuthMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.AuthLoginRequest;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import com.qivo.security.AccountPrincipal;
import com.qivo.security.SessionRegistry;
import com.qivo.service.AdminService;
import com.qivo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "公共-登录认证")
@RestController
@RequestMapping("/api/common/auth")
public class AuthController {

    private final UserService userService;
    private final AdminService adminService;
    private final RedisStore redisStore;
    private final SessionRegistry sessions;

    public AuthController(
            UserService userService,
            AdminService adminService,
            RedisStore redisStore,
            SessionRegistry sessions) {
        this.userService = userService;
        this.adminService = adminService;
        this.redisStore = redisStore;
        this.sessions = sessions;
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @Log(value = "用户登录", type = "LOGIN")
    public Result<Map<String, Object>> login(@Valid @RequestBody AuthLoginRequest params) {
        validateCaptcha(params.captchaKey(), params.captchaCode());
        Map<String, Object> result = userService.login(params.username(), params.password());
        return Result.success(result);
    }

    @Operation(summary = "管理员登录")
    @PostMapping("/adminLogin")
    @Log(value = "管理员登录", type = "LOGIN")
    public Result<Map<String, Object>> adminLogin(@Valid @RequestBody AuthLoginRequest params) {
        validateCaptcha(params.captchaKey(), params.captchaCode());
        Map<String, Object> result = adminService.login(params.username(), params.password());
        return Result.success(result);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal AccountPrincipal principal) {
        sessions.revoke(principal.sessionId());
        return Result.success();
    }

    @Operation(summary = "未登录提示")
    @GetMapping("/unLogin")
    public Result<Void> unLogin() {
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    @Operation(summary = "无权限提示")
    @GetMapping("/unauthorized")
    public Result<Void> unauthorized() {
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /** 验证码校验：从 Redis 读取 → 比对 → 一次性删除 */
    private void validateCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            throw new BusinessException(400, AuthMessageConstant.CAPTCHA_REQUIRED);
        }
        String cachedCode =
                (String) redisStore.getAndDelete(SecurityConstant.CAPTCHA_KEY_PREFIX + captchaKey);
        if (cachedCode == null) {
            throw new BusinessException(400, AuthMessageConstant.CAPTCHA_EXPIRED);
        }
        if (!cachedCode.equalsIgnoreCase(captchaCode)) {
            throw new BusinessException(400, AuthMessageConstant.CAPTCHA_INCORRECT);
        }
    }
}
