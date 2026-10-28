package com.qivo.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.constant.RoleMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.dto.AssignPermissionsRequest;
import com.qivo.dto.RoleCopyRequest;
import com.qivo.dto.RoleWriteRequest;
import com.qivo.entity.Permission;
import com.qivo.entity.Role;
import com.qivo.entity.RoleChange;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.PermissionMapper;
import com.qivo.mapper.RoleChangeMapper;
import com.qivo.mapper.RoleMapper;
import com.qivo.persistence.PageRequests;
import com.qivo.result.ErrorCode;
import com.qivo.security.AccountPrincipal;
import com.qivo.security.AdminAccessPolicy;
import com.qivo.service.RoleService;
import com.qivo.vo.PageVO;
import com.qivo.vo.RoleMemberVO;
import com.qivo.vo.RolePreviewVO;
import com.qivo.vo.RoleWorkspaceVO;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final RoleChangeMapper changes;
    private final ObjectMapper json;

    public RoleServiceImpl(RoleMapper roles, PermissionMapper permissions,
            RoleChangeMapper changes, ObjectMapper json) {
        this.roles = roles;
        this.permissions = permissions;
        this.changes = changes;
        this.json = json;
    }

    @Override public List<Role> list() { return roles.selectAll(); }
    @Override public Role getById(Long id) { return roles.selectById(id); }
    @Override public List<Permission> permissionTree() { return permissions.selectAll(); }
    @Override public List<Permission> getPermissionsByRoleId(Long id) {
        required(id, false);
        return permissions.selectByRoleId(id);
    }
    @Override public List<Role> getRolesByAdminId(Long id) { return roles.selectByAdminId(id); }

    private Role required(Long id, boolean lock) {
        Role role = lock ? roles.lockById(id) : roles.selectById(id);
        if (role == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return role;
    }

    private boolean builtIn(Role role) {
        return SecurityConstant.BUILTIN_SUPER_ADMIN_CODE.equals(role.getCode());
    }

    private void editable(Role role, Long expectedVersion) {
        if (builtIn(role)) throw new BusinessException(409, RoleMessageConstant.BUILTIN_READ_ONLY);
        if (expectedVersion == null || !expectedVersion.equals(role.getVersion()))
            throw new BusinessException(409, RoleMessageConstant.CONFLICT);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleWorkspaceVO workspace(Long id) {
        var role = required(id, false);
        role.setMemberCount(roles.memberCount(id));
        var catalog = permissions.selectAll();
        var assigned = permissions.selectByRoleId(id);
        return new RoleWorkspaceVO(role, builtIn(role), catalog,
                assigned.stream().map(Permission::getId).toList(), describe(role, assigned, assigned));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RolePreviewVO preview(AssignPermissionsRequest request) {
        var role = required(request.roleId(), false);
        editable(role, request.version());
        role.setMemberCount(roles.memberCount(role.getId()));
        return describe(role, permissions.selectByRoleId(role.getId()),
                normalize(permissions.selectAll(), request.permissionIds()));
    }

    private List<Permission> normalize(List<Permission> catalog, List<Long> selected) {
        Map<Long, Permission> byId = catalog.stream().collect(Collectors.toMap(Permission::getId, x -> x));
        Set<Long> parents = catalog.stream().map(Permission::getParentId).collect(Collectors.toSet());
        Set<Long> ids = new HashSet<>();
        for (Long id : new HashSet<>(selected)) {
            if (!byId.containsKey(id)) throw new BusinessException(ErrorCode.BAD_REQUEST);
            if (!parents.contains(id)) ids.add(id);
        }
        for (Long id : new ArrayList<>(ids)) {
            Set<Long> visited = new HashSet<>();
            Long parent = byId.get(id).getParentId();
            while (byId.containsKey(parent) && visited.add(parent)) {
                ids.add(parent);
                parent = byId.get(parent).getParentId();
            }
        }
        return catalog.stream().filter(p -> ids.contains(p.getId())).toList();
    }

    private RolePreviewVO describe(Role role, List<Permission> before, List<Permission> after) {
        Set<Long> oldIds = before.stream().map(Permission::getId).collect(Collectors.toSet());
        Set<Long> newIds = after.stream().map(Permission::getId).collect(Collectors.toSet());
        Set<Long> parents = permissions.selectAll().stream().map(Permission::getParentId).collect(Collectors.toSet());
        var added = after.stream().filter(p -> !oldIds.contains(p.getId()) && !parents.contains(p.getId())).toList();
        var removed = before.stream().filter(p -> !newIds.contains(p.getId()) && !parents.contains(p.getId())).toList();
        Set<String> grants = after.stream().map(Permission::getCode).collect(Collectors.toSet());
        grants.add(SecurityConstant.AUTHORITY_ADMIN);
        if (builtIn(role)) grants.add(SecurityConstant.AUTHORITY_SUPER_ADMIN);
        var capabilities = AdminAccessPolicy.capabilities(grants);
        var warnings = new ArrayList<String>();
        if (!builtIn(role) && after.stream().noneMatch(p -> !parents.contains(p.getId())))
            warnings.add("该角色尚未授权业务功能，关联账号仍可登录并管理本人资料");
        if ((capabilities.get("product:add") || capabilities.get("product:update")) && !capabilities.get("product:list"))
            warnings.add("已允许商品操作，但没有查看商品权限，关联账号无法从商品列表使用这些操作");
        if (capabilities.get("order:ship") && !capabilities.get("order:list"))
            warnings.add("已允许发货，但没有查看订单权限，关联账号无法从订单列表发货");
        if (grants.contains("product:delete"))
            warnings.add("“删除商品”目前没有开放业务入口，勾选不会增加商品删除能力");
        if (!builtIn(role) && after.stream().anyMatch(p -> Set.of("role:manage", "admin:add", "admin:update").contains(p.getCode())))
            warnings.add("角色授权和管理员账号写操作仍仅向系统超级管理员开放");
        return new RolePreviewVO(role.getVersion(), role.getMemberCount(), added, removed, capabilities, warnings);
    }

    private Role newRole(RoleWriteRequest request) {
        if (SecurityConstant.BUILTIN_SUPER_ADMIN_CODE.equalsIgnoreCase(request.code()))
            throw new BusinessException(409, RoleMessageConstant.BUILTIN_READ_ONLY);
        if (roles.byCode(request.code()) != null)
            throw new BusinessException(409, RoleMessageConstant.CODE_EXISTS);
        var role = new Role();
        role.setName(request.name().trim());
        if (role.getName().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST);
        role.setCode(request.code());
        role.setDescription(request.description() == null ? "" : request.description().trim());
        role.setVersion(0L);
        try { roles.insert(role); }
        catch (DuplicateKeyException e) { throw new BusinessException(409, RoleMessageConstant.CODE_EXISTS); }
        return required(role.getId(), false);
    }

    @Override @Transactional
    public Role add(RoleWriteRequest request, AccountPrincipal actor) {
        var role = newRole(request);
        record(role, "CREATE", actor, null, snapshot(role), "新建自定义角色，尚未分配给账号");
        return role;
    }

    @Override @Transactional
    public Role update(RoleWriteRequest request, AccountPrincipal actor) {
        if (request.id() == null) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var role = required(request.id(), true);
        editable(role, request.version());
        if (!role.getCode().equals(request.code()))
            throw new BusinessException(409, "角色创建后编码不可更改，请修改名称或说明");
        var before = snapshot(role);
        role.setName(request.name().trim());
        role.setDescription(request.description() == null ? "" : request.description().trim());
        if (role.getName().isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST);
        roles.update(role);
        advance(role);
        record(role, "UPDATE", actor, before, snapshot(role), "更新角色名称或说明");
        return required(role.getId(), false);
    }

    @Override @Transactional
    public Role copy(Long sourceId, RoleCopyRequest request, AccountPrincipal actor) {
        var source = required(sourceId, true);
        editable(source, request.sourceVersion());
        var role = newRole(request.role());
        var assigned = normalize(permissions.selectAll(), permissions.selectByRoleId(sourceId).stream().map(Permission::getId).toList());
        for (var p : assigned) permissions.insertRolePermission(role.getId(), p.getId());
        record(role, "COPY", actor, null, snapshot(role), "复制自“" + source.getName() + "”，未自动分配给账号");
        return role;
    }

    @Override @Transactional
    public Role assignPermissions(AssignPermissionsRequest request, AccountPrincipal actor) {
        var role = required(request.roleId(), true);
        editable(role, request.version());
        var assigned = normalize(permissions.selectAll(), request.permissionIds());
        Set<Long> oldIds = permissions.selectByRoleId(role.getId()).stream().map(Permission::getId).collect(Collectors.toSet());
        Set<Long> newIds = assigned.stream().map(Permission::getId).collect(Collectors.toSet());
        if (oldIds.equals(newIds)) return role;
        var before = snapshot(role);
        permissions.deleteRolePermissions(role.getId());
        for (var p : assigned) permissions.insertRolePermission(role.getId(), p.getId());
        advance(role);
        record(role, "PERMISSIONS", actor, before, snapshot(role), "更新功能权限");
        return required(role.getId(), false);
    }

    private void advance(Role role) {
        if (roles.advanceVersion(role.getId(), role.getVersion()) != 1)
            throw new BusinessException(409, RoleMessageConstant.CONFLICT);
        role.setVersion(role.getVersion() + 1);
    }

    @Override @Transactional
    public void delete(Long id, Long version, AccountPrincipal actor) {
        var role = required(id, true);
        editable(role, version);
        if (roles.bindings(id) > 0)
            throw new BusinessException(409, "该角色仍有关联管理员，请先在关联账号中调整角色");
        var before = snapshot(role);
        permissions.deleteRolePermissions(id);
        roles.deleteById(id);
        record(role, "DELETE", actor, before, null, "删除无关联账号的自定义角色");
    }

    private String snapshot(Role role) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("name", role.getName());
        snapshot.put("code", role.getCode());
        snapshot.put("description", role.getDescription());
        snapshot.put("version", role.getVersion());
        Set<Long> parents = permissions.selectAll().stream().map(Permission::getParentId).collect(Collectors.toSet());
        snapshot.put("permissions", permissions.selectByRoleId(role.getId()).stream().map(p ->
                Map.of("id", p.getId(), "name", p.getName(), "code", p.getCode(), "leaf", !parents.contains(p.getId()))).toList());
        try { return json.writeValueAsString(snapshot); }
        catch (JsonProcessingException e) { throw new IllegalStateException("角色快照无法保存", e); }
    }

    private void record(Role role, String action, AccountPrincipal actor, String before, String after, String note) {
        var log = new RoleChange();
        log.setRoleId(role.getId()); log.setRoleName(role.getName()); log.setAction(action);
        log.setActorId(actor.id()); log.setActorName(actor.username());
        log.setBeforeSnapshot(before); log.setAfterSnapshot(after); log.setNote(note);
        changes.insert(log);
    }

    @Override @Transactional(readOnly = true)
    public PageVO<RoleMemberVO> members(Long id, int page, int size, String keyword, Integer status) {
        required(id, false);
        if (status != null && status != 0 && status != 1) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var pagination = PageRequests.<RoleMemberVO>of(page, size);
        var rows = roles.members(pagination, id, keyword, status);
        return PageVO.of(rows, pagination.getTotal(), page, size);
    }

    @Override @Transactional(readOnly = true)
    public PageVO<RoleChange> history(Long id, int page, int size, String operator, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new BusinessException(ErrorCode.BAD_REQUEST);
        var pagination = PageRequests.<RoleChange>of(page, size);
        var rows = changes.search(pagination, id, operator, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay());
        return PageVO.of(rows, pagination.getTotal(), page, size);
    }
}
