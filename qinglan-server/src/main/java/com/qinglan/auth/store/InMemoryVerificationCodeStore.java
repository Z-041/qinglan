package com.qinglan.auth.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存实现，用于本地开发与测试（无需 Redis）。
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.auth", name = "code-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_KEY_PREFIX = "ql:auth:code:";
    private static final String ATTEMPT_KEY_PREFIX = "ql:auth:attempt:";
    private static final String SEND_KEY_PREFIX = "ql:auth:send:";

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void saveCode(String emailHash, String code, Duration ttl) {
        entries.put(CODE_KEY_PREFIX + emailHash, Entry.of(code, ttl));
    }

    @Override
    public Optional<String> findCode(String emailHash) {
        return read(CODE_KEY_PREFIX + emailHash);
    }

    @Override
    public long incrementAttempts(String emailHash, Duration ttl) {
        String key = ATTEMPT_KEY_PREFIX + emailHash;
        Entry updated = entries.compute(key, (ignored, current) -> {
            if (current == null || current.isExpiredAt(Instant.now())) {
                return Entry.of("1", ttl);
            }
            return new Entry(String.valueOf(current.valueAsLong() + 1), current.expiresAt());
        });
        return updated.valueAsLong();
    }

    @Override
    public void removeCode(String emailHash) {
        entries.remove(CODE_KEY_PREFIX + emailHash);
        entries.remove(ATTEMPT_KEY_PREFIX + emailHash);
    }

    @Override
    public boolean tryAcquireSendSlot(String emailHash, Duration interval) {
        String key = SEND_KEY_PREFIX + emailHash;
        Entry fresh = Entry.of("1", interval);
        Entry existing = entries.putIfAbsent(key, fresh);
        if (existing == null) {
            return true;
        }
        if (existing.isExpiredAt(Instant.now())) {
            entries.put(key, fresh);
            return true;
        }
        return false;
    }

    private Optional<String> read(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpiredAt(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
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
