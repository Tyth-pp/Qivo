package com.qivo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "请输入当前密码") @Size(max = 72, message = "当前密码输入过长，请检查后重试") String oldPassword,
        @NotBlank(message = "请设置新密码") @Size(min = 8, max = 72, message = "新密码需为8至72位") String newPassword) {}
