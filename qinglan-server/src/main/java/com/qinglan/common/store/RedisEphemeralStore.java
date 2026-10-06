package com.qinglan.common.store;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 实现，用于多实例部署（AGENTS.md Redis key 约定 ql:{domain}:{id}）。
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.store", name = "type", havingValue = "redis")
@RequiredArgsConstructor
public class RedisEphemeralStore implements EphemeralStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            // 只在首次写入时设置过期时间，否则每次自增都会把窗口无限延长
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 1L : value;
    }

    @Override
    public boolean markUsedOnce(String key, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
    }
}
