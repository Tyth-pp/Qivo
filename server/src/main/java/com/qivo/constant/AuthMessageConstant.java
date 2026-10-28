package com.qivo.constant;

/** 认证与验证码提示，不重复定义 ErrorCode 已有文案。 */
public final class AuthMessageConstant {

    private AuthMessageConstant() {}

    public static final String CAPTCHA_REQUIRED = "验证码不能为空";
    public static final String CAPTCHA_EXPIRED = "验证码已过期，请重新获取";
    public static final String CAPTCHA_INCORRECT = "验证码错误";
    public static final String AUTH_SERVICE_UNAVAILABLE = "认证服务暂不可用";
    public static final String SESSION_EXPIRED = "会话已失效";
}
