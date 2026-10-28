package com.qivo.constant;

/** 账号、密码与内置角色的业务提示。 */
public final class AccountMessageConstant {

    private AccountMessageConstant() {}

    public static final String USERNAME_REQUIREMENTS = "用户名需为3至50位字母、数字或下划线，暂不支持中文";
    public static final String PASSWORD_REQUIREMENTS = "密码至少8位，UTF-8编码不超过72字节";
    public static final String PASSWORD_RECOVERY_UNAVAILABLE = "找回密码暂未开放，请联系管理员处理";
    public static final String BUILTIN_ROLE_CREATE_FORBIDDEN = "内置角色不可重复创建";
    public static final String BUILTIN_ROLE_UPDATE_FORBIDDEN = "内置超级管理员角色不可修改";
    public static final String BUILTIN_ROLE_DELETE_FORBIDDEN = "内置超级管理员角色不可删除";
}
