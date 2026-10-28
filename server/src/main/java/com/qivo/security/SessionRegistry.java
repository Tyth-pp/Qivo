package com.qivo.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class SessionRegistry {
    private static final String SESSION_KEY_PREFIX = "qivo:session:v2:";
    private static final String SESSION_VERSION_KEY_PREFIX = "qivo:session-version:v2:";

    private final StringRedisTemplate redis;

    public SessionRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String id) {
        return SESSION_KEY_PREFIX + id;
    }

    public void open(String id, String subject, long ttlMillis) {
        redis.opsForValue().set(key(id), subject, Duration.ofMillis(ttlMillis));
    }

    public boolean active(String id, String subject) {
        return subject.equals(redis.opsForValue().get(key(id)));
    }

    public void revoke(String id) {
        redis.delete(key(id));
    }

    public String version(String subject) {
        String value = redis.opsForValue().get(SESSION_VERSION_KEY_PREFIX + subject);
        return value == null ? "0" : value;
    }

    public void revokeAll(String subject) {
        redis.opsForValue().increment(SESSION_VERSION_KEY_PREFIX + subject);
    }
}
