package com.qinglan.common.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单机内存实现，用于本地开发与测试（无需 Redis）。
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.store", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryEphemeralStore implements EphemeralStore {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public long increment(String key, Duration ttl) {
        Entry updated = entries.compute(key, (ignored, current) -> {
            if (current == null || current.isExpiredAt(Instant.now())) {
                return Entry.of("1", ttl);
            }
            return new Entry(String.valueOf(current.valueAsLong() + 1), current.expiresAt());
        });
        return updated.valueAsLong();
    }

    @Override
    public boolean markUsedOnce(String key, Duration ttl) {
        AtomicBoolean claimed = new AtomicBoolean(false);
        // compute 对同一个 key 是原子的，因此并发下只有一个线程能把 claimed 置为 true
        entries.compute(key, (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || current.isExpiredAt(now)) {
                claimed.set(true);
                return Entry.of("1", ttl);
            }
            return current;
        });
        return claimed.get();
    }

    private record Entry(String value, Instant expiresAt) {

        static Entry of(String value, Duration ttl) {
            return new Entry(value, Instant.now().plus(ttl));
        }

        boolean isExpiredAt(Instant moment) {
            return !expiresAt.isAfter(moment);
        }

        long valueAsLong() {
            return Long.parseLong(value);
        }
    }
}
