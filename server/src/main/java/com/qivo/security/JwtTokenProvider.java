package com.qivo.security;

import com.qivo.constant.AuthMessageConstant;
import com.qivo.constant.SecurityConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long expiration;
    private final SessionRegistry sessions;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:604800000}") long expiration,
            SessionRegistry sessions) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.sessions = sessions;
    }

    public String generateToken(Long id, String username, String domain, String encodedPassword) {
        String sid = UUID.randomUUID().toString();
        String subject = domain + ":" + id;
        String token =
                Jwts.builder()
                        .issuer(SecurityConstant.JWT_ISSUER)
                        .subject(subject)
                        .id(sid)
                        .claim(SecurityConstant.JWT_USER_ID, id)
                        .claim(SecurityConstant.JWT_USERNAME, username)
                        .claim(SecurityConstant.JWT_DOMAIN, domain)
                        .claim(
                                SecurityConstant.JWT_PASSWORD_STAMP,
                                Passwords.stamp(encodedPassword))
                        .claim(SecurityConstant.JWT_SESSION_VERSION, sessions.version(subject))
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + expiration))
                        .signWith(key)
                        .compact();
        sessions.open(sid, subject, expiration);
        return token;
    }

    public Claims parse(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(SecurityConstant.JWT_ISSUER)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        if (claims.getId() == null
                || !sessions.active(claims.getId(), claims.getSubject())
                || !Objects.equals(
                        claims.get(SecurityConstant.JWT_SESSION_VERSION, String.class),
                        sessions.version(claims.getSubject())))
            throw new BadCredentialsException(AuthMessageConstant.SESSION_EXPIRED);
        return claims;
    }
}
