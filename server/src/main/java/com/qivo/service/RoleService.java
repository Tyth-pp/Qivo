package com.qivo.service;

import com.qivo.entity.Permission;
import com.qivo.entity.Role;
import com.qivo.entity.RoleChange;
import com.qivo.dto.AssignPermissionsRequest;
import com.qivo.dto.RoleWriteRequest;
import com.qivo.dto.RoleCopyRequest;
import com.qivo.security.AccountPrincipal;
import com.qivo.vo.PageVO;
import com.qivo.vo.RoleMemberVO;
import com.qivo.vo.RolePreviewVO;
import com.qivo.vo.RoleWorkspaceVO;
import java.time.LocalDate;

import java.util.List;

public interface RoleService {
    List<Role> list();

    Role getById(Long id);

    Role add(RoleWriteRequest role, AccountPrincipal actor);

    Role update(RoleWriteRequest role, AccountPrincipal actor);

    void delete(Long id, Long version, AccountPrincipal actor);

    List<Permission> permissionTree();

    List<Permission> getPermissionsByRoleId(Long roleId);

    Role assignPermissions(AssignPermissionsRequest request, AccountPrincipal actor);

    Role copy(Long sourceId, RoleCopyRequest request, AccountPrincipal actor);
    RoleWorkspaceVO workspace(Long id);
    RolePreviewVO preview(AssignPermissionsRequest request);
    PageVO<RoleMemberVO> members(Long id, int page, int size, String keyword, Integer status);
    PageVO<RoleChange> history(Long id, int page, int size, String operator, LocalDate from, LocalDate to);

    List<Role> getRolesByAdminId(Long adminId);
}
