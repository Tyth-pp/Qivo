package com.qivo.controller.admin;

import com.qivo.annotation.Log;
import com.qivo.entity.User;
import com.qivo.service.UserService;
import com.qivo.result.Result;
import com.qivo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "后台-用户管理")
@RestController
@RequestMapping("/api/admin/user")
public class UserManageController {

    private final UserService userService;

    public UserManageController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户列表")
    @GetMapping("/list")
    public Result<PageVO<User>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(userService.page(page, pageSize, keyword));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/detail/{userId}")
    public Result<User> detail(@PathVariable Long userId) {
        return Result.success(userService.getById(userId));
    }

    @Operation(summary = "禁用/启用用户")
    @PutMapping("/status/{userId}")
    @Log(value = "修改用户状态", type = "UPDATE")
    public Result<Void> updateStatus(
            @PathVariable Long userId, @RequestBody Map<String, Integer> params) {
        userService.updateStatus(userId, params.get("status"));
        return Result.success();
    }

    @Operation(summary = "修改用户积分")
    @PutMapping("/score/{userId}")
    @Log(value = "修改用户积分", type = "UPDATE")
    public Result<Void> updateScore(
            @PathVariable Long userId, @RequestBody Map<String, Integer> params) {
        userService.updateScore(userId, params.get("score"));
        return Result.success();
    }
}
