package com.qivo.controller.admin;

import com.qivo.annotation.Log;
import com.qivo.dto.AdminCreateRequest;
import com.qivo.dto.AssignPermissionsRequest;
import com.qivo.dto.RoleWriteRequest;
import com.qivo.dto.RoleCopyRequest;
import com.qivo.vo.RoleWorkspaceVO;
import com.qivo.vo.RolePreviewVO;
import com.qivo.vo.RoleMemberVO;
import com.qivo.vo.PageVO;
import com.qivo.entity.RoleChange;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.qivo.dto.AdminAccountRequest;
import com.qivo.dto.AdminAccountUpdateRequest;
import com.qivo.dto.AdminStatusRequest;
import com.qivo.dto.AdminResetPasswordRequest;
import com.qivo.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.qivo.entity.Admin;
import com.qivo.entity.Permission;
import com.qivo.entity.Role;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import com.qivo.service.AdminService;
import com.qivo.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后台-角色权限")
@RestController
@RequestMapping("/api/admin")
public class RoleController {

    private final RoleService roleService;
    private final AdminService adminService;

    public RoleController(RoleService roleService, AdminService adminService) {
        this.roleService = roleService;
        this.adminService = adminService;
    }

    // ---------- 角色 ----------
    @Operation(summary = "角色列表")
    @GetMapping("/role/list")
    public Result<List<Role>> roleList() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "新增角色")
    @PostMapping("/role/add")
    @Log(value = "新增角色", type = "INSERT")
    public Result<Role> addRole(@Valid @RequestBody RoleWriteRequest role,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return Result.success(roleService.add(role, actor));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/role/update")
    @Log(value = "修改角色", type = "UPDATE")
    public Result<Role> updateRole(@Valid @RequestBody RoleWriteRequest role,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return Result.success(roleService.update(role, actor));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/role/{id}")
    @Log(value = "删除角色", type = "DELETE")
    public Result<Void> deleteRole(@PathVariable Long id, @RequestParam Long version,
            @AuthenticationPrincipal AccountPrincipal actor) {
        roleService.delete(id, version, actor);
        return Result.success();
    }

    // ---------- 权限 ----------
    @Operation(summary = "权限树")
    @GetMapping("/permission/tree")
    public Result<List<Permission>> permissionTree() {
        return Result.success(roleService.permissionTree());
    }

    @Operation(summary = "获取角色已有权限")
    @GetMapping("/role/{roleId}/permissions")
    public Result<List<Permission>> rolePermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getPermissionsByRoleId(roleId));
    }

    @Operation(summary = "给角色分配权限")
    @PostMapping("/role/permission")
    @Log(value = "分配角色权限", type = "UPDATE")
    public Result<Role> assignPermission(@Valid @RequestBody AssignPermissionsRequest params,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return Result.success(roleService.assignPermissions(params, actor));
    }

    @GetMapping("/role/{id}/workspace")
    public Result<RoleWorkspaceVO> workspace(@PathVariable Long id) {
        return Result.success(roleService.workspace(id));
    }

    @PostMapping("/role/preview")
    public Result<RolePreviewVO> preview(@Valid @RequestBody AssignPermissionsRequest request) {
        return Result.success(roleService.preview(request));
    }

    @PostMapping("/role/{id}/copy")
    public Result<Role> copy(@PathVariable Long id, @Valid @RequestBody RoleCopyRequest request,
            @AuthenticationPrincipal AccountPrincipal actor) {
        return Result.success(roleService.copy(id, request, actor));
    }

    @GetMapping("/role/{id}/members")
    public Result<PageVO<RoleMemberVO>> members(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer status) {
        return Result.success(roleService.members(id, page, pageSize, keyword, status));
    }

    @GetMapping("/role/{id}/history")
    public Result<PageVO<RoleChange>> history(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(roleService.history(id, page, pageSize, operator, from, to));
    }

    @GetMapping("/role/history")
    public Result<PageVO<RoleChange>> allHistory(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.success(roleService.history(null, page, pageSize, operator, from, to));
    }

    // ---------- 管理员账号 ----------
    @Operation(summary = "管理员列表")
    @GetMapping("/admin/list")
    public Result<?> adminList(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer status) {
        return Result.success(adminService.accounts(page,pageSize,keyword,status));
    }

    @Operation(summary = "新增管理员")
    @PostMapping("/admin/add")
    @Log(value = "新增管理员", type = "INSERT")
    public Result<Void> addAdmin(@Valid @RequestBody AdminAccountRequest params) {
        adminService.createAccount(params);
        return Result.success();
    }

    @PutMapping("/admin/{id}")
    public Result<Void> updateAdmin(@PathVariable Long id,@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody AdminAccountUpdateRequest request) {
        adminService.updateAccount(id,actor.id(),request);return Result.success();
    }
    @PutMapping("/admin/{id}/status")
    public Result<Void> status(@PathVariable Long id,@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody AdminStatusRequest request) {
        adminService.setStatus(id,actor.id(),request.status());return Result.success();
    }
    @PutMapping("/admin/{id}/password")
    public Result<Void> password(@PathVariable Long id,@AuthenticationPrincipal AccountPrincipal actor,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        adminService.resetPassword(id,actor.id(),request.password());return Result.success();
    }

    @Operation(summary = "给管理员分配角色")
    @PutMapping("/admin/role")
    @Log(value = "分配管理员角色", type = "UPDATE")
    public Result<Void> assignAdminRole(@RequestBody Map<String, Long> params,@AuthenticationPrincipal AccountPrincipal actor) {
        Long adminId = params.get("adminId"), roleId = params.get("roleId");
        if (adminId == null || roleId == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
        if(adminId.equals(actor.id()))throw new BusinessException(409,"不能在账号管理中更改自己的角色");
        adminService.assignRole(adminId, roleId);
        return Result.success();
    }
}
