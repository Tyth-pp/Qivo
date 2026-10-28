package com.qivo;

import com.qivo.entity.*;
import com.qivo.entity.Order;
import com.qivo.entity.AfterSale;
import com.qivo.service.AfterSaleService;
import com.qivo.exception.BusinessException;
import com.qivo.result.ErrorCode;
import com.qivo.mapper.*;
import com.qivo.security.*;
import com.qivo.security.JwtTokenProvider;
import com.qivo.service.FileStorageService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FoundationSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean UserMapper users;
    @MockitoBean AdminMapper admins;
    @MockitoBean RoleMapper roles;
    @MockitoBean PermissionMapper permissions;
    @MockitoBean SessionRegistry sessions;
    @MockitoBean FileStorageService files;
    @MockitoBean OrderMapper orders;
    @MockitoBean OrderItemMapper orderItems;
    @MockitoBean AfterSaleService afterSales;
    private User user;
    private Admin admin;

    @BeforeEach
    void setup() {
        Map<String, String> active = new HashMap<>();
        when(sessions.version(anyString())).thenReturn("0");
        doAnswer(
                        i -> {
                            active.put(i.getArgument(0), i.getArgument(1));
                            return null;
                        })
                .when(sessions)
                .open(anyString(), anyString(), anyLong());
        when(sessions.active(anyString(), anyString()))
                .thenAnswer(i -> Objects.equals(active.get(i.getArgument(0)), i.getArgument(1)));
        doAnswer(
                        i -> {
                            active.remove(i.getArgument(0));
                            return null;
                        })
                .when(sessions)
                .revoke(anyString());
        user = new User();
        user.setId(7L);
        user.setUsername("buyer");
        user.setStatus(1);
        user.setPassword("stored-user-hash");
        admin = new Admin();
        admin.setId(7L);
        admin.setUsername("operator");
        admin.setStatus(1);
        admin.setPassword("stored-admin-hash");
        when(users.selectById(7L)).thenReturn(user);
        when(admins.selectById(7L)).thenReturn(admin);
        when(roles.selectByAdminId(7L)).thenReturn(List.of());
    }

    @Test
    void adminOrderDetailRequiresCurrentOrderReadPermissionAndKeepsSnapshotFields() throws Exception {
        String token = operator();
        mvc.perform(get("/api/admin/order/detail/71")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/order/detail/71").header("Authorization", "Bearer " + buyer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/order/detail/71").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orders, orderItems);
        Role role = new Role();
        role.setId(2L);
        role.setCode("order_reader");
        Permission permission = new Permission();
        permission.setCode("order:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(permission));
        Order order = new Order();
        order.setId(71L);
        order.setOrderNo("HISTORY-71");
        OrderItem item = new OrderItem();
        item.setId(72L);
        item.setProductName("购买时名称");
        item.setSkuSnapshotSource("LEGACY");
        when(orders.selectById(71L)).thenReturn(order);
        when(orderItems.selectByOrderId(71L)).thenReturn(List.of(item));
        mvc.perform(get("/api/admin/order/detail/71").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("HISTORY-71"))
                .andExpect(jsonPath("$.data.items[0].productName").value("购买时名称"))
                .andExpect(jsonPath("$.data.items[0].skuSnapshotSource").value("LEGACY"))
                .andExpect(jsonPath("$.data.items[0].skuSpecSnapshot").isEmpty());
        mvc.perform(put("/api/admin/order/ship/71").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"trackingNo\":\"TEST\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/order/detail/99999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(3001));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/order/detail/71").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAccountReadDoesNotGrantWritesOrLeakPassword() throws Exception {
        Role role=new Role();role.setId(2L);role.setCode("account_reader");
        Permission permission=new Permission();permission.setCode("admin:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(permission));
        when(admins.search(any(),any(),any())).thenReturn(List.of(admin));
        String token=operator();
        mvc.perform(get("/api/admin/admin/list").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].username").value("operator"))
                .andExpect(jsonPath("$.data.records[0].password").doesNotExist());
        mvc.perform(put("/api/admin/admin/8/status").header("Authorization","Bearer "+token)
                        .contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/admin/list").header("Authorization","Bearer "+buyer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void afterSaleUsesAuthenticatedOwnerAndValidatesRequestKey() throws Exception {
        var row=new AfterSale();row.setId(71L);row.setOrderId(701L);row.setUserId(7L);
        when(afterSales.apply(7L,701L,"request-71","申请原因")).thenReturn(row);
        mvc.perform(post("/api/fore/after-sale").header("Authorization","Bearer "+buyer())
                        .header("Idempotency-Key","request-71").contentType("application/json")
                        .content("{\"orderId\":701,\"reason\":\"申请原因\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(7));
        verify(afterSales).apply(7L,701L,"request-71","申请原因");
        mvc.perform(post("/api/fore/after-sale").header("Authorization","Bearer "+buyer())
                        .header("Idempotency-Key","bad").contentType("application/json")
                        .content("{\"orderId\":701,\"reason\":\"申请原因\"}"))
                .andExpect(status().isBadRequest());
        when(afterSales.history(7L,999L)).thenThrow(new BusinessException(ErrorCode.ORDER_NOT_EXIST));
        mvc.perform(get("/api/fore/after-sale/order/999").header("Authorization","Bearer "+buyer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/fore/after-sale/order/701").header("Authorization","Bearer "+operator()))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderReadDoesNotGrantAfterSaleApproval() throws Exception {
        Role role=new Role();role.setId(2L);role.setCode("order_reader");
        Permission permission=new Permission();permission.setCode("order:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(permission));
        mvc.perform(post("/api/admin/after-sale/71/review").header("Authorization","Bearer "+operator())
                        .contentType("application/json").content("{\"approve\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/after-sale/71/review").header("Authorization","Bearer "+buyer())
                        .contentType("application/json").content("{\"approve\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(afterSales);
    }

    private String buyer() {
        return jwt.generateToken(7L, "buyer", "USER", user.getPassword());
    }

    private String operator() {
        return jwt.generateToken(7L, "operator", "ADMIN", admin.getPassword());
    }

    @Test
    void anonymousAndMalformedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/fore/cart/list"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/admin/profile").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fore/product/collect")).andExpect(status().isUnauthorized());
    }

    @Test
    void equalIdsCannotCrossIdentityDomains() throws Exception {
        mvc.perform(get("/api/admin/profile").header("Authorization", "Bearer " + buyer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileDoesNotLeakPasswordAndLogoutRevokesToken() throws Exception {
        String token = buyer();
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("buyer"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(post("/api/common/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountsAndChangedPasswordsInvalidateExistingSessions() throws Exception {
        String token = buyer();
        user.setStatus(0);
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        user.setStatus(1);
        user.setPassword("new-hash");
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminNeedsRealPermissionForProductReadAndWrite() throws Exception {
        String token = operator();
        mvc.perform(get("/api/admin/product/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        Role role = new Role();
        role.setId(2L);
        role.setCode("product_admin");
        Permission p = new Permission();
        p.setCode("product:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(p));
        mvc.perform(get("/api/admin/product/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/admin/product/add")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redisFailureDoesNotBypassAuthentication() throws Exception {
        String token = buyer();
        when(sessions.active(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("test"));
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidLoginPayloadIsBadRequest() throws Exception {
        mvc.perform(post("/api/common/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutOnlyRevokesItsOwnSession() throws Exception {
        String first = buyer(), second = buyer();
        mvc.perform(post("/api/common/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void limitedAdminCannotManageUsersRolesOrUseRevokedPermissions() throws Exception {
        Role role = new Role();
        role.setId(2L);
        role.setCode("product_admin");
        Permission permission = new Permission();
        permission.setCode("product:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(permission));
        String token = operator();
        mvc.perform(get("/api/admin/product/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/user/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/role/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/role/permission").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        when(permissions.selectByRoleId(2L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/product/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyProductEditorsCanUploadImages() throws Exception {
        var image = new MockMultipartFile("file", "demo.png", "image/png", new byte[] {1, 2});
        mvc.perform(multipart("/api/common/upload/file").file(image))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/common/upload/file").file(image)
                        .header("Authorization", "Bearer " + buyer()))
                .andExpect(status().isForbidden());
        String token = operator();
        mvc.perform(multipart("/api/common/upload/file").file(image)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        Role role = new Role();
        role.setId(2L);
        role.setCode("product_admin");
        Permission permission = new Permission();
        permission.setCode("product:update");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(permission));
        when(files.upload(any())).thenReturn("/upload/test/demo.png");
        mvc.perform(multipart("/api/common/upload/file").file(image)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(files, times(1)).upload(any());
    }

    @Test
    void accessSummaryIsRestrictedToTheCurrentAdministrator() throws Exception {
        mvc.perform(get("/api/admin/access")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer " + buyer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/access?adminId=999").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.adminId").value(7))
                .andExpect(jsonPath("$.data.username").value("operator"))
                .andExpect(jsonPath("$.data.capabilities['product:list']").value(false))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void accessSummaryTracksRealPermissionsAndDoesNotGrantRoleWritesToRoleReaders() throws Exception {
        Role role = new Role();
        role.setId(2L);
        role.setCode("custom_reader");
        role.setName("超级管理员");
        Permission productRead = new Permission();
        productRead.setCode("product:list");
        Permission roleRead = new Permission();
        roleRead.setCode("role:list");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(2L)).thenReturn(List.of(productRead, roleRead));
        String token = operator();
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities['product:list']").value(true))
                .andExpect(jsonPath("$.data.capabilities['product:update']").value(false))
                .andExpect(jsonPath("$.data.capabilities['role:list']").value(true))
                .andExpect(jsonPath("$.data.capabilities['role:write']").value(false))
                .andExpect(jsonPath("$.data.capabilities['order:price']").value(false));
        mvc.perform(put("/api/admin/product/update").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        when(permissions.selectByRoleId(2L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities['product:list']").value(false));
    }

    @Test
    void builtInSuperAdministratorRetainsExistingSpecialOperations() throws Exception {
        Role role = new Role();
        role.setId(1L);
        role.setCode("super_admin");
        when(roles.selectByAdminId(7L)).thenReturn(List.of(role));
        when(permissions.selectByRoleId(1L)).thenReturn(List.of());
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer " + operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities['dashboard']").value(true))
                .andExpect(jsonPath("$.data.capabilities['role:write']").value(true))
                .andExpect(jsonPath("$.data.capabilities['order:price']").value(true));
    }

    @Test
    void selfProfileCannotEscalateAndAvatarUploadDoesNotGrantProductUpload() throws Exception {
        String token = operator();
        mvc.perform(put("/api/admin/profile").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"id\":999,\"realName\":\"本人\",\"avatar\":\"\",\"status\":0}"))
                .andExpect(status().isOk());
        verify(admins).updateProfile(7L, "本人", "");
        verify(admins, never()).update(any());
        mvc.perform(put("/api/admin/profile").header("Authorization", "Bearer " + buyer())
                .contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/common/upload/avatar").file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
        for (String identity : List.of(token, buyer())) {
            mvc.perform(multipart("/api/common/upload/avatar").file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}))
                    .header("Authorization", "Bearer " + identity)).andExpect(status().isOk());
        }
        mvc.perform(multipart("/api/common/upload/file").file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}))
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void changedUserPasswordInvalidatesOldTokenAndRejectsCrossDomain() throws Exception {
        Passwords codec = new Passwords(); user.setPassword(codec.encode("beforePassword9"));
        String oldToken = buyer();
        when(users.changePasswordIfUnchanged(eq(7L), anyString(), anyString())).thenAnswer(invocation -> {
            user.setPassword(invocation.getArgument(2)); return 1;
        });
        mvc.perform(put("/api/fore/user/password").header("Authorization", "Bearer " + oldToken)
                .contentType("application/json").content("{\"oldPassword\":\"beforePassword9\",\"newPassword\":\"afterPassword9\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/fore/user/profile").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/fore/user/password").header("Authorization", "Bearer " + operator())
                .contentType("application/json").content("{\"oldPassword\":\"beforePassword9\",\"newPassword\":\"afterPassword9\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerValidationReturnsReadableChineseInsteadOfRegexAndFieldNames() throws Exception {
        mvc.perform(post("/api/fore/user/register").contentType("application/json")
                .content("{\"username\":\"中文用户\",\"password\":\"validPassword9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("暂不支持中文")))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("username:"))))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("正则"))));
        mvc.perform(post("/api/fore/user/register").contentType("application/json")
                .content("{\"username\":\"valid_user\",\"password\":\"short\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("密码需为8至72位")))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("邮箱格式不正确")));
        verify(users, never()).insert(any());
    }
}
