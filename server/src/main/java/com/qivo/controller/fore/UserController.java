package com.qivo.controller.fore;

import com.qivo.constant.AccountMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.RegisterRequest;
import com.qivo.dto.PasswordChangeRequest;
import com.qivo.entity.Address;
import com.qivo.entity.User;
import com.qivo.exception.BusinessException;
import com.qivo.result.Result;
import com.qivo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@Tag(name = "前台-用户")
@RestController
@RequestMapping("/api/fore/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest input) {
        User user = new User();
        user.setUsername(input.username());
        user.setPassword(input.password());
        user.setEmail(input.email());
        user.setPhone(input.phone());
        userService.register(user);
        return Result.success();
    }

    @Operation(summary = "获取个人信息")
    @GetMapping("/profile")
    public Result<User> profile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        return Result.success(userService.getById(userId));
    }

    @Operation(summary = "修改个人信息")
    @PutMapping("/profile")
    public Result<Void> updateProfile(HttpServletRequest request, @RequestBody User user) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        user.setId(userId);
        userService.updateProfile(user);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(HttpServletRequest request, @Valid @RequestBody PasswordChangeRequest input) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        userService.updatePassword(userId, input.oldPassword(), input.newPassword());
        return Result.success();
    }

    @Operation(summary = "找回密码")
    @PostMapping("/resetPassword")
    public Result<Void> resetPassword(@RequestBody Map<String, String> params) {
        throw new BusinessException(409, AccountMessageConstant.PASSWORD_RECOVERY_UNAVAILABLE);
    }

    // ---------- 收货地址 ----------
    @Operation(summary = "获取收货地址列表")
    @GetMapping("/address")
    public Result<List<Address>> addressList(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        return Result.success(userService.addressList(userId));
    }

    @Operation(summary = "新增收货地址")
    @PostMapping("/address")
    public Result<Void> addAddress(HttpServletRequest request, @RequestBody Address address) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        address.setUserId(userId);
        userService.addAddress(address);
        return Result.success();
    }

    @Operation(summary = "修改收货地址")
    @PutMapping("/address/{id}")
    public Result<Void> updateAddress(
            @PathVariable Long id, HttpServletRequest request, @RequestBody Address address) {
        address.setUserId((Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID));
        address.setId(id);
        userService.updateAddress(address);
        return Result.success();
    }

    @Operation(summary = "删除收货地址")
    @DeleteMapping("/address/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        userService.deleteAddress(id, userId);
        return Result.success();
    }
}
