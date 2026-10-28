package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.cache.RedisStore;
import com.qivo.dto.AssignPermissionsRequest;
import com.qivo.dto.RoleCopyRequest;
import com.qivo.dto.RoleWriteRequest;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.RoleChangeMapper;
import com.qivo.security.AccountPrincipal;
import com.qivo.security.JwtTokenProvider;
import com.qivo.security.SessionRegistry;
import com.qivo.service.RoleService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roleworkspace;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.sql.init.schema-locations=classpath:operations-test-schema.sql,classpath:role-test-schema.sql"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RoleWorkspaceTests {
    @Autowired RoleService roles;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper json;
    @MockitoBean RedisStore redis;
    @MockitoBean SessionRegistry sessions;
    @MockitoSpyBean RoleChangeMapper changes;
    final AccountPrincipal actor = new AccountPrincipal(101L, "supervisor", "ADMIN", "isolated");

    @BeforeEach
    void seed() {
        reset(changes);
        for (String table : List.of("role_change_log", "role_permission", "permission", "admin_role", "admin", "role"))
            db.update("DELETE FROM " + table);
        db.update("INSERT INTO role(id,name,code,description,version) VALUES(1,'超级管理员','super_admin','系统内置',0),(2,'商品管理员','product_manager','负责商品',0),(3,'权限查看员','role_reader','只读',0)");
        db.update("INSERT INTO admin(id,username,password,status,real_name) VALUES(101,'supervisor','isolated-hash',1,'管理者'),(102,'reader','isolated-hash',1,'查看员'),(103,'member','isolated-hash',0,'停用成员')");
        db.update("INSERT INTO admin_role(admin_id,role_id) VALUES(101,1),(102,3),(103,2)");
        db.update("INSERT INTO permission(id,name,code,parent_id,type,sort_order) VALUES(10,'商品管理','product',0,1,1),(11,'查看商品','product:list',10,1,2),(12,'编辑商品','product:update',10,2,3),(13,'删除商品','product:delete',10,2,4),(20,'订单管理','order',0,1,5),(21,'查看订单','order:list',20,1,6),(22,'发货','order:ship',20,2,7),(31,'查看角色','role:list',0,1,8)");
        db.update("INSERT INTO role_permission(role_id,permission_id) VALUES(2,10),(2,11),(3,31)");
        when(sessions.version(anyString())).thenReturn("0");
        when(sessions.active(anyString(), anyString())).thenReturn(true);
    }

    String token(long id) {
        return "Bearer " + jwt.generateToken(id, id == 101 ? "supervisor" : "reader", "ADMIN", "isolated-hash");
    }
    int count(String sql) { return db.queryForObject(sql, Integer.class); }

    @Test
    void workspaceAndPreviewReflectActualPolicyWithoutExpandingParents() {
        var initial = roles.workspace(2L);
        assertEquals(1, initial.role().getMemberCount());
        assertTrue(initial.preview().capabilities().get("product:list"));
        assertFalse(initial.preview().capabilities().get("product:update"));
        var empty = roles.preview(new AssignPermissionsRequest(2L, List.of(10L), 0L));
        assertFalse(empty.capabilities().get("product:list"));
        assertFalse(empty.capabilities().get("product:update"));
        var editOnly = roles.preview(new AssignPermissionsRequest(2L, List.of(12L), 0L));
        assertTrue(editOnly.capabilities().get("product:update"));
        assertFalse(editOnly.capabilities().get("role:write"));
        assertEquals(1, editOnly.added().size());
        assertEquals(1, editOnly.removed().size());
        assertFalse(editOnly.warnings().isEmpty());
        assertEquals(0, count("SELECT COUNT(*) FROM role_change_log"));
    }

    @Test
    void saveIsVersionedAuditedAndNoOpDoesNotProduceAnotherChange() throws Exception {
        roles.assignPermissions(new AssignPermissionsRequest(2L, List.of(11L, 12L, 12L), 0L), actor);
        assertEquals(1L, roles.workspace(2L).role().getVersion());
        assertEquals(3, count("SELECT COUNT(*) FROM role_permission WHERE role_id=2"));
        roles.assignPermissions(new AssignPermissionsRequest(2L, List.of(11L, 12L), 1L), actor);
        assertEquals(1, count("SELECT COUNT(*) FROM role_change_log"));
        var change = roles.history(2L, 1, 10, "super", null, null).getRecords().get(0);
        assertEquals("supervisor", change.getActorName());
        assertEquals(2, json.readTree(change.getBeforeSnapshot()).path("permissions").size());
        assertEquals(3, json.readTree(change.getAfterSnapshot()).path("permissions").size());
        assertThrows(BusinessException.class, () -> roles.assignPermissions(new AssignPermissionsRequest(2L, List.of(), 0L), actor));
        assertEquals(3, count("SELECT COUNT(*) FROM role_permission WHERE role_id=2"));
    }

    @Test
    void concurrentWritersHaveOneWinnerAndOneConflict() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> atGate(gate, List.of(11L, 12L)));
            var b = pool.submit(() -> atGate(gate, List.of(21L, 22L)));
            gate.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(1L, roles.workspace(2L).role().getVersion());
        assertEquals(1, count("SELECT COUNT(*) FROM role_change_log"));
        assertEquals(3, count("SELECT COUNT(*) FROM role_permission WHERE role_id=2"));
    }

    private boolean atGate(CountDownLatch gate, List<Long> ids) throws InterruptedException {
        gate.await();
        try { roles.assignPermissions(new AssignPermissionsRequest(2L, ids, 0L), actor); return true; }
        catch (BusinessException e) { assertEquals(409, e.getCode()); return false; }
    }

    @Test
    void copyCreatesIndependentRoleWithoutMembersAndChecksSourceVersion() {
        var copy = roles.copy(2L, new RoleCopyRequest(0L,
                new RoleWriteRequest(null, "商品查看副本", "product_copy", "独立配置", null)), actor);
        assertEquals(0, roles.workspace(copy.getId()).role().getMemberCount());
        assertEquals(2, roles.workspace(copy.getId()).permissionIds().size());
        roles.assignPermissions(new AssignPermissionsRequest(copy.getId(), List.of(12L), 0L), actor);
        assertFalse(roles.workspace(2L).preview().capabilities().get("product:update"));
        assertThrows(BusinessException.class, () -> roles.copy(copy.getId(), new RoleCopyRequest(0L,
                new RoleWriteRequest(null, "旧副本", "stale_copy", "", null)), actor));
        assertEquals(0, count("SELECT COUNT(*) FROM role WHERE code='stale_copy'"));
    }

    @Test
    void builtInRoleIsReadOnlyEvenWhenRequestBypassesThePage() {
        assertTrue(roles.workspace(1L).builtIn());
        assertTrue(roles.workspace(1L).preview().capabilities().get("admin:write"));
        assertThrows(BusinessException.class, () -> roles.assignPermissions(new AssignPermissionsRequest(1L, List.of(), 0L), actor));
        assertThrows(BusinessException.class, () -> roles.delete(1L, 0L, actor));
        assertThrows(BusinessException.class, () -> roles.update(new RoleWriteRequest(1L, "改名", "super_admin", "", 0L), actor));
        assertThrows(BusinessException.class, () -> roles.copy(1L, new RoleCopyRequest(0L,
                new RoleWriteRequest(null, "内置副本", "super_copy", "", null)), actor));
        assertEquals(0, count("SELECT COUNT(*) FROM role_change_log"));
    }

    @Test
    void readOnlyRoleCanInspectButCannotMutateOrSpoofTheActor() throws Exception {
        http.perform(get("/api/admin/role/2/workspace").header("Authorization", token(102))).andExpect(status().isOk());
        http.perform(post("/api/admin/role/preview").header("Authorization", token(102))
                .contentType("application/json").content("{\"roleId\":2,\"permissionIds\":[12],\"version\":0}"))
                .andExpect(status().isOk());
        http.perform(post("/api/admin/role/permission").header("Authorization", token(102))
                .contentType("application/json").content("{\"roleId\":2,\"permissionIds\":[12],\"version\":0,\"actorId\":101}"))
                .andExpect(status().isForbidden());
        http.perform(post("/api/admin/role/2/copy").header("Authorization", token(102))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        http.perform(get("/api/admin/role/2/members").header("Authorization", token(102)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].password").doesNotExist());
        assertEquals(0, count("SELECT COUNT(*) FROM role_change_log"));
    }

    @Test
    void roleFormsHaveFieldFeedbackAndCannotSkipTheVersion() throws Exception {
        http.perform(post("/api/admin/role/add").header("Authorization", token(101))
                .contentType("application/json").content("{\"name\":\"\",\"code\":\"中文\"}"))
                .andExpect(status().isBadRequest());
        http.perform(post("/api/admin/role/permission").header("Authorization", token(101))
                .contentType("application/json").content("{\"roleId\":2,\"permissionIds\":[]}"))
                .andExpect(status().isBadRequest());
        assertThrows(BusinessException.class, () -> roles.add(new RoleWriteRequest(null, "重复", "product_manager", "", null), actor));
        roles.update(new RoleWriteRequest(2L, "新的角色名", "product_manager", "说明", 0L), actor);
        assertEquals(1L, roles.workspace(2L).role().getVersion());
        assertThrows(BusinessException.class, () -> roles.assignPermissions(new AssignPermissionsRequest(2L, List.of(12L), 0L), actor));
    }

    @Test
    void auditFailureRollsBackPermissionsAndVersion() {
        doThrow(new DataAccessResourceFailureException("isolated audit failure")).when(changes).insert(any());
        assertThrows(DataAccessResourceFailureException.class,
                () -> roles.assignPermissions(new AssignPermissionsRequest(2L, List.of(12L), 0L), actor));
        assertEquals(0L, roles.workspace(2L).role().getVersion());
        assertTrue(roles.workspace(2L).preview().capabilities().get("product:list"));
        assertFalse(roles.workspace(2L).preview().capabilities().get("product:update"));
    }

    @Test
    void membersFiltersAndDeletionPreserveHistoricalEvidence() {
        assertEquals(1, roles.members(2L, 1, 1, "member", 0).getTotal());
        assertEquals(0, roles.members(2L, 1, 10, null, 1).getTotal());
        assertThrows(BusinessException.class, () -> roles.delete(2L, 0L, actor));
        var role = roles.add(new RoleWriteRequest(null, "可清理角色", "deletable", "", null), actor);
        roles.delete(role.getId(), 0L, actor);
        assertNull(roles.getById(role.getId()));
        assertEquals(2, roles.history(role.getId(), 1, 10, "super", LocalDate.now(), LocalDate.now()).getTotal());
        assertEquals("DELETE", roles.history(role.getId(), 1, 1, null, null, null).getRecords().get(0).getAction());
    }
}
