package com.qivo.security;

import com.qivo.constant.SecurityConstant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 后台接口授权与前端可用操作共用规则；可用操作名称不新增数据库权限码。 */
public final class AdminAccessPolicy {
    private AdminAccessPolicy() {}

    public static boolean allows(Set<String> granted, String path, String method) {
        if (!granted.contains(SecurityConstant.AUTHORITY_ADMIN)) return false;
        if (granted.contains(SecurityConstant.AUTHORITY_SUPER_ADMIN)) return true;
        if (path.equals("/api/common/upload/file")) {
            return granted.contains("product:add") || granted.contains("product:update");
        }
        String required = permission(path, method);
        return required != null && granted.contains(required);
    }

    private static String permission(String path, String method) {
        if (path.equals("/api/admin/role/preview") && "POST".equals(method)) return "role:list";
        if (path.startsWith("/api/admin/admin/")) return "GET".equals(method)?"admin:list":null;
        if (path.startsWith("/api/admin/after-sale/")) return "order:refund";
        if (path.equals("/api/admin/access") || path.equals("/api/admin/profile")
                || path.equals("/api/admin/password")) return SecurityConstant.AUTHORITY_ADMIN;
        if (path.startsWith("/api/admin/product/category/"))
            return "GET".equals(method) ? "category:list" : "product:update";
        if (path.equals("/api/admin/product/add")) return "product:add";
        if (path.startsWith("/api/admin/product/"))
            return "GET".equals(method) ? "product:list" : "product:update";
        if (path.startsWith("/api/admin/order/ship/")) return "order:ship";
        if (path.startsWith("/api/admin/order/refund/")) return "order:refund";
        if (path.startsWith("/api/admin/order/")) return "GET".equals(method) ? "order:list" : null;
        if (path.startsWith("/api/admin/user/"))
            return "GET".equals(method) ? "user:list" : "user:disable";
        if (path.startsWith("/api/admin/role/") || path.startsWith("/api/admin/permission/"))
            return "GET".equals(method) ? "role:list" : null;
        if (path.startsWith("/api/admin/statistics/"))
            return "GET".equals(method) ? "dashboard" : null;
        return null;
    }

    public static Map<String, Boolean> capabilities(Set<String> granted) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("dashboard", allows(granted, "/api/admin/statistics/workbench", "GET"));
        result.put("product:list", allows(granted, "/api/admin/product/list", "GET"));
        result.put("category:list", allows(granted, "/api/admin/product/category/list", "GET"));
        result.put("order:list", allows(granted, "/api/admin/order/list", "GET"));
        result.put("user:list", allows(granted, "/api/admin/user/list", "GET"));
        result.put("role:list", allows(granted, "/api/admin/role/list", "GET"));
        result.put("product:add", allows(granted, "/api/admin/product/add", "POST"));
        result.put("product:update", allows(granted, "/api/admin/product/update", "PUT"));
        result.put("order:ship", allows(granted, "/api/admin/order/ship/1", "PUT"));
        result.put("order:price", allows(granted, "/api/admin/order/price/1", "PUT"));
        result.put("user:disable", allows(granted, "/api/admin/user/status/1", "PUT"));
        result.put("role:write", allows(granted, "/api/admin/role/permission", "POST"));
        result.put("admin:list", allows(granted, "/api/admin/admin/list", "GET"));
        result.put("admin:write", allows(granted, "/api/admin/admin/add", "POST"));
        result.put("order:refund", allows(granted, "/api/admin/after-sale/list", "GET"));
        return Map.copyOf(result);
    }
}
