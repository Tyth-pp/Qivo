package com.qivo.security;

public record AccountPrincipal(Long id, String username, String domain, String sessionId) {}
