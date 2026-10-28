package com.qivo.dto;

import com.qivo.constant.AccountMessageConstant;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = AccountMessageConstant.USERNAME_REQUIREMENTS) String username,
        @NotBlank(message = "请设置登录密码") @Size(min = 8, max = 72, message = "密码需为8至72位") String password,
        @Email(message = "邮箱格式不正确，请填写如 name@example.com 的地址")
        @Size(max = 100, message = "邮箱地址不能超过100个字符") String email,
        @Size(max = 20, message = "联系电话不能超过20个字符") String phone) {}
