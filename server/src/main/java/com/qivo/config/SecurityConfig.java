package com.qivo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qivo.constant.SecurityConstant;
import com.qivo.mapper.*;
import com.qivo.result.ErrorCode;
import com.qivo.result.Result;
import com.qivo.security.AdminAccessPolicy;
import com.qivo.security.JwtAuthenticationFilter;
import com.qivo.security.JwtTokenProvider;
import java.util.stream.Collectors;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            JwtTokenProvider jwt,
            UserMapper users,
            AdminMapper admins,
            RoleMapper roles,
            PermissionMapper permissions,
            ObjectMapper json)
            throws Exception {
        http.csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) -> {
                                                    res.setStatus(401);
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            Result.fail(ErrorCode.UNAUTHORIZED));
                                                })
                                        .accessDeniedHandler(
                                                (req, res, ex) -> {
                                                    res.setStatus(403);
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            Result.fail(ErrorCode.FORBIDDEN));
                                                }))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/common/captcha/get",
                                                "/api/fore/index/**",
                                                "/api/fore/product/list",
                                                "/api/fore/product/detail/*",
                                                "/api/fore/comment/list/*",
                                                "/upload/**",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/common/auth/login",
                                                "/api/common/auth/adminLogin",
                                                "/api/fore/user/register")
                                        .permitAll()
                                        .requestMatchers(
                                                "/doc.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/webjars/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/common/upload/file")
                                        .access(
                                                (authentication, context) -> {
                                                    var granted =
                                                            authentication.get().getAuthorities().stream()
                                                                    .map(x -> x.getAuthority())
                                                                    .collect(Collectors.toSet());
                                                    return new AuthorizationDecision(
                                                            AdminAccessPolicy.allows(granted,
                                                                    context.getRequest().getRequestURI(),
                                                                    context.getRequest().getMethod()));
                                                })
                                        .requestMatchers("/api/admin/**")
                                        .access(
                                                (authentication, context) -> {
                                                    var auth = authentication.get();
                                                    var granted =
                                                            auth.getAuthorities().stream()
                                                                    .map(x -> x.getAuthority())
                                                                    .collect(Collectors.toSet());
                                                    return new AuthorizationDecision(
                                                            AdminAccessPolicy.allows(granted,
                                                                    context.getRequest().getRequestURI(),
                                                                    context.getRequest().getMethod()));
                                                })
                                        .requestMatchers("/api/fore/**")
                                        .hasRole(SecurityConstant.USER_ROLE_NAME)
                                        .requestMatchers("/actuator/**")
                                        .hasAuthority(SecurityConstant.AUTHORITY_SUPER_ADMIN)
                                        .requestMatchers("/api/common/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwt, users, admins, roles, permissions, json),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
