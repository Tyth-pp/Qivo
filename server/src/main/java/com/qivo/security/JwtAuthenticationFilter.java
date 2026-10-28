package com.qivo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.constant.AuthMessageConstant;
import com.qivo.constant.SecurityConstant;
import com.qivo.entity.*;
import com.qivo.mapper.*;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwt;
    private final UserMapper users;
    private final AdminMapper admins;
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwt,
            UserMapper users,
            AdminMapper admins,
            RoleMapper roles,
            PermissionMapper permissions,
            ObjectMapper json) {
        this.jwt = jwt;
        this.users = users;
        this.admins = admins;
        this.roles = roles;
        this.permissions = permissions;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(req, resp);
            return;
        }
        try {
            if (!header.startsWith(SecurityConstant.BEARER_PREFIX))
                throw new IllegalArgumentException();
            Claims c = jwt.parse(header.substring(7));
            Long id = c.get(SecurityConstant.JWT_USER_ID, Number.class).longValue();
            String domain = c.get(SecurityConstant.JWT_DOMAIN, String.class);
            if (!(domain + ":" + id).equals(c.getSubject())) throw new IllegalArgumentException();
            Integer status;
            String password;
            String username;
            Set<String> authority = new HashSet<>();
            if (SecurityConstant.DOMAIN_ADMIN.equals(domain)) {
                Admin a = admins.selectById(id);
                if (a == null) throw new IllegalArgumentException();
                status = a.getStatus();
                password = a.getPassword();
                username = a.getUsername();
                authority.add(SecurityConstant.AUTHORITY_ADMIN);
                for (Role role : roles.selectByAdminId(id)) {
                    if (SecurityConstant.BUILTIN_SUPER_ADMIN_CODE.equals(role.getCode()))
                        authority.add(SecurityConstant.AUTHORITY_SUPER_ADMIN);
                    for (Permission p : permissions.selectByRoleId(role.getId()))
                        authority.add(p.getCode());
                }
            } else if (SecurityConstant.DOMAIN_USER.equals(domain)) {
                User u = users.selectById(id);
                if (u == null) throw new IllegalArgumentException();
                status = u.getStatus();
                password = u.getPassword();
                username = u.getUsername();
                authority.add(SecurityConstant.AUTHORITY_USER);
            } else throw new IllegalArgumentException();
            if (!Integer.valueOf(1).equals(status)
                    || !Passwords.stamp(password)
                    .equals(c.get(SecurityConstant.JWT_PASSWORD_STAMP, String.class)))
                throw new IllegalArgumentException();
            AccountPrincipal principal = new AccountPrincipal(id, username, domain, c.getId());
            var auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authority.stream().map(SimpleGrantedAuthority::new).toList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            req.setAttribute(SecurityConstant.REQUEST_USER_ID, id);
            req.setAttribute(SecurityConstant.REQUEST_USERNAME, username);
        } catch (DataAccessException e) {
            fail(resp, 503, AuthMessageConstant.AUTH_SERVICE_UNAVAILABLE);
            return;
        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException
                 | AuthenticationException
                 | NullPointerException e) {
            fail(resp, 401, ErrorCode.UNAUTHORIZED.getMsg());
            return;
        }
        chain.doFilter(req, resp);
    }

    private void fail(HttpServletResponse resp, int status, String message) throws IOException {
        SecurityContextHolder.clearContext();
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        json.writeValue(resp.getOutputStream(), Result.fail(status, message));
    }
}
