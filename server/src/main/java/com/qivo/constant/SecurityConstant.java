package com.qivo.constant;

/** 认证协议与跨组件共享标识；取值变化需要兼容性评估。 */
public final class SecurityConstant {

    private SecurityConstant() {}

    public static final String DOMAIN_USER = "USER";
    public static final String DOMAIN_ADMIN = "ADMIN";
    public static final String USER_ROLE_NAME = "USER";
    public static final String AUTHORITY_USER = "ROLE_USER";
    public static final String AUTHORITY_ADMIN = "ROLE_ADMIN";
    public static final String AUTHORITY_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String BUILTIN_SUPER_ADMIN_CODE = "super_admin";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String JWT_ISSUER = "qivo-v2";
    public static final String JWT_USER_ID = "userId";
    public static final String JWT_USERNAME = "username";
    public static final String JWT_DOMAIN = "domain";
    public static final String JWT_PASSWORD_STAMP = "stamp";
    public static final String JWT_SESSION_VERSION = "epoch";
    public static final String REQUEST_USER_ID = "userId";
    public static final String REQUEST_USERNAME = "username";
    public static final String CAPTCHA_KEY_PREFIX = "captcha:";
}
