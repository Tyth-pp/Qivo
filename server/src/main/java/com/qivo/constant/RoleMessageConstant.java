package com.qivo.constant;

public final class RoleMessageConstant {
    private RoleMessageConstant() {}
    public static final String CONFLICT = "角色已被其他管理员修改，请重新加载后核对，当前修改尚未保存";
    public static final String BUILTIN_READ_ONLY = "系统内置角色拥有全部管理权限，不支持修改或复制";
    public static final String CODE_EXISTS = "角色编码已被使用，请换一个编码";
}
