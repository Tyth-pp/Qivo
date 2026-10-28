package com.qivo.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 首页缓存读写与失效；不承担商品查询和事务编排。 */
@Component
public class HomepageCache {
    private static final String KEY = "qivo:index:data:v2";
    private final RedisStore redisStore;

    public HomepageCache(RedisStore redisStore) {
        this.redisStore = redisStore;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> read() {
        Object cached = redisStore.get(KEY);
        return cached instanceof Map ? (Map<String, Object>) cached : null;
    }

    public void write(Map<String, Object> data) {
        redisStore.set(KEY, data, 30, TimeUnit.MINUTES);
    }

    public void evict() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            redisStore.delete(KEY);
                        }
                    });
        } else redisStore.delete(KEY);
    }
}
