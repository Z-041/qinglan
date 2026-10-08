package com.qinglan.auth.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
        Instant now = Instant.now();
        // 「查是否过期 → 覆盖」必须是一次原子操作：拆成两步（先查后 put）时，
        // 两个并发请求会同时看到旧槽位已过期、各自覆盖后都返回 true —— 同一邮箱双发验证码。
        // compute 对同一个 key 加锁，天然串行化。
        AtomicBoolean acquired = new AtomicBoolean(false);
        entries.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpiredAt(now)) {
                acquired.set(true);
                return Entry.of("1", interval);
            }
            return existing;
        });
        return acquired.get();
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
