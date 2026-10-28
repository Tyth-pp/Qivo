package com.qivo.dto;

import jakarta.validation.constraints.*;

public record AuthLoginRequest(
        @NotBlank(message = "请输入登录账号") @Size(max = 50, message = "登录账号不能超过50位") String username,
        @NotBlank(message = "请输入密码") @Size(max = 72, message = "密码输入过长，请检查后重试") String password,
        @NotBlank(message = "请重新获取图片验证码") @Size(max = 100, message = "验证码已失效，请刷新后重试") String captchaKey,
        @NotBlank(message = "请输入图片中的验证码") @Size(max = 10, message = "验证码输入过长，请按图片内容填写") String captchaCode) {}
