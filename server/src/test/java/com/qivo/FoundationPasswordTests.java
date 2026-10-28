package com.qivo;

import com.qivo.entity.Admin;
import com.qivo.entity.User;
import com.qivo.service.impl.AdminServiceImpl;
import com.qivo.exception.BusinessException;
import com.qivo.mapper.*;
import com.qivo.security.*;
import com.qivo.security.JwtTokenProvider;
import com.qivo.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoundationPasswordTests {
    private final Passwords passwords = new Passwords();

    @Test
    void bcryptOnlyAndLengthLimits() {
        String hash = passwords.encode("password123");
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwords.matches("password123", hash));
        assertFalse(passwords.matches("wrong", hash));
        assertFalse(passwords.matches(null, hash));
        assertFalse(passwords.matches("123456", "e10adc3949ba59abbe56e057f20f883e"));
        assertFalse(passwords.matches("password123", "malformed"));
        assertThrows(BusinessException.class, () -> passwords.encode("short"));
        assertThrows(BusinessException.class, () -> passwords.encode("汉".repeat(25)));
    }

    @Test
    void bcryptLoginDoesNotRewritePasswordBeforeIssuingDomainScopedToken() {
        UserMapper users = mock(UserMapper.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        User u = new User();
        u.setId(1L);
        u.setUsername("buyer");
        u.setStatus(1);
        u.setPassword(passwords.encode("password123"));
        String old = u.getPassword();
        when(users.selectByUsername("buyer")).thenReturn(u);
        UserServiceImpl service =
                new UserServiceImpl(
                        users,
                        mock(AddressMapper.class),
                        jwt,
                        passwords,
                        mock(SessionRegistry.class));
        service.login("buyer", "password123");
        assertEquals(old, u.getPassword());
        verify(users).selectByUsername("buyer");
        verifyNoMoreInteractions(users);
        verify(jwt).generateToken(eq(1L), eq("buyer"), eq("USER"), eq(u.getPassword()));
    }

    @Test
    void legacyAccountCannotLoginOrIssueToken() {
        UserMapper users = mock(UserMapper.class);
        JwtTokenProvider jwt = mock(JwtTokenProvider.class);
        User user = new User();
        user.setId(1L);
        user.setUsername("legacy");
        user.setStatus(1);
        user.setPassword("e10adc3949ba59abbe56e057f20f883e");
        when(users.selectByUsername("legacy")).thenReturn(user);
        UserServiceImpl service =
                new UserServiceImpl(
                        users,
                        mock(AddressMapper.class),
                        jwt,
                        passwords,
                        mock(SessionRegistry.class));
        assertThrows(BusinessException.class, () -> service.login("legacy", "123456"));
        verifyNoInteractions(jwt);
    }

    @Test
    void passwordChangeRetainsConcurrentUpdateProtection() {
        AdminMapper admins = mock(AdminMapper.class);
        Admin admin = new Admin();
        admin.setId(1L);
        String old = passwords.encode("password123");
        admin.setPassword(old);
        when(admins.selectById(1L)).thenReturn(admin);
        when(admins.changePasswordIfUnchanged(eq(1L), eq(old), anyString())).thenReturn(0);
        var service =
                new AdminServiceImpl(
                        admins, mock(RoleMapper.class), mock(JwtTokenProvider.class), passwords, mock(SessionRegistry.class));
        assertThrows(
                BusinessException.class,
                () -> service.updatePassword(1L, "password123", "newPassword123"));
        verify(admins)
                .changePasswordIfUnchanged(
                        eq(1L),
                        eq(old),
                        argThat(hash -> passwords.matches("newPassword123", hash)));
    }

    @Test
    void profileWriteCannotModifyPasswordOrAccountStatus() {
        UserMapper users = mock(UserMapper.class);
        UserServiceImpl service =
                new UserServiceImpl(
                        users,
                        mock(AddressMapper.class),
                        mock(JwtTokenProvider.class),
                        passwords,
                        mock(SessionRegistry.class));
        User input = new User();
        input.setId(1L);
        input.setPassword("injected");
        input.setStatus(1);
        input.setScore(999);
        input.setEmail("safe@example.com");
        when(users.selectById(1L)).thenReturn(new User());
        service.updateProfile(input);
        verify(users)
                .update(
                        argThat(
                                x ->
                                        x.getPassword() == null
                                                && x.getStatus() == null
                                                && x.getScore() == null
                                                && "safe@example.com".equals(x.getEmail())));
    }
}
