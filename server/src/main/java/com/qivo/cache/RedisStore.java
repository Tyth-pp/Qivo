package com.qivo.cache;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 操作封装
 */
@Component
public class RedisStore {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object getAndDelete(String key) {
        // Lua 原子消费，兼容不支持 GETDEL 的旧 Redis。
        return redisTemplate.execute(
                new DefaultRedisScript<>(
                        "local value=redis.call('GET',KEYS[1]); if value then redis.call('DEL',KEYS[1]); end; return value",
                        String.class),
                List.of(key));
    }

    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // ---------- Hash ----------
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public void hDel(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }

    // ---------- Common ----------
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    public void delete(String... keys) {
        if (keys.length == 1) {
            redisTemplate.delete(keys[0]);
        } else {
            redisTemplate.delete(Arrays.asList(keys));
        }
    }

    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }
}
