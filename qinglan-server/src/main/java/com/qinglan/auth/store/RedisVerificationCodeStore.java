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
        if (attempts == null) {
            // Redis 返回空值（连接异常等）：与旧实现一致按「第 1 次」处理，不把 null 传给调用方
            return 1L;
        }
        if (attempts == 1L) {
            redisTemplate.expire(key, ttl);
        } else {
            // 兜底：key 存在却没有 TTL（例如上次 INCR 之后、EXPIRE 之前进程中断）——
            // 那样计数会永不衰减，用户被永久锁在「尝试次数过多」。补设一次即可自愈。
            Long remaining = redisTemplate.getExpire(key);
            if (remaining == null || remaining < 0) {
                redisTemplate.expire(key, ttl);
            }
        }
        return attempts;
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
