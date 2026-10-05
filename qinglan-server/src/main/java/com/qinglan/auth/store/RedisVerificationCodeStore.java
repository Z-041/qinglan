package com.qinglan.auth.store;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Redis 实现，用于多实例部署（AGENTS.md Redis key 约定 ql:{domain}:{id}）。
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.auth", name = "code-store", havingValue = "redis")
@RequiredArgsConstructor
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String CODE_KEY_PREFIX = "ql:auth:code:";
    private static final String ATTEMPT_KEY_PREFIX = "ql:auth:attempt:";
    private static final String SEND_KEY_PREFIX = "ql:auth:send:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveCode(String emailHash, String code, Duration ttl) {
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + emailHash, code, ttl);
    }

    @Override
    public Optional<String> findCode(String emailHash) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CODE_KEY_PREFIX + emailHash));
    }

    @Override
    public long incrementAttempts(String emailHash, Duration ttl) {
        String key = ATTEMPT_KEY_PREFIX + emailHash;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return attempts == null ? 1L : attempts;
    }

    @Override
    public void removeCode(String emailHash) {
        redisTemplate.delete(List.of(CODE_KEY_PREFIX + emailHash, ATTEMPT_KEY_PREFIX + emailHash));
    }

    @Override
    public boolean tryAcquireSendSlot(String emailHash, Duration interval) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(SEND_KEY_PREFIX + emailHash, "1", interval));
    }
}
