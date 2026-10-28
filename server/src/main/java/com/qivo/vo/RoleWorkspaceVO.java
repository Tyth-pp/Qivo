package com.qivo.vo;

import com.qivo.entity.Permission;
import com.qivo.entity.Role;
import java.util.List;

public record RoleWorkspaceVO(Role role, boolean builtIn, List<Permission> catalog,
        List<Long> permissionIds, RolePreviewVO preview) {}
