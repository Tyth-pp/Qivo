package com.qivo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.qivo.cache.HomepageCache;
import com.qivo.cache.RedisStore;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class HomepageCacheTests {
    @Test
    void extractionKeepsTheExistingCacheKeyTtlAndInvalidation() {
        RedisStore store = mock(RedisStore.class);
        HomepageCache cache = new HomepageCache(store);
        Map<String, Object> data = Map.of("hotProducts", List.of());
        when(store.get("qivo:index:data:v2")).thenReturn(data);
        assertSame(data, cache.read());
        cache.write(data);
        cache.evict();
        verify(store).set("qivo:index:data:v2", data, 30, TimeUnit.MINUTES);
        verify(store).delete("qivo:index:data:v2");
    }

    @Test
    void mutationEvictsOnlyAfterCommitAndDoesNotEvictOnRollback() {
        RedisStore store = mock(RedisStore.class);
        HomepageCache cache = new HomepageCache(store);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict();
            verifyNoInteractions(store);
            var hooks = TransactionSynchronizationManager.getSynchronizations();
            hooks.forEach(h -> h.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(store);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict();
            verifyNoInteractions(store);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(store).delete("qivo:index:data:v2");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void missingOrInvalidCacheStillFallsBackToTheService() {
        RedisStore store = mock(RedisStore.class);
        HomepageCache cache = new HomepageCache(store);
        assertNull(cache.read());
        when(store.get("qivo:index:data:v2")).thenReturn("invalid");
        assertNull(cache.read());
    }
}
