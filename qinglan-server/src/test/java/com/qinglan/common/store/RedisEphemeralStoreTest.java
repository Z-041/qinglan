package com.qinglan.common.store;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 短时效存储的真跑集成测试（R-36 遗留）。
 *
 * <p>仅当 {@code QINGLAN_REDIS_TEST=true} 时运行；本地无 Redis 时自动跳过。</p>
 */
@EnabledIfEnvironmentVariable(named = "QINGLAN_REDIS_TEST", matches = "true")
class RedisEphemeralStoreTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    private RedisEphemeralStore store;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(redisHost(), redisPort());
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("窗口内计数累加，并在首次写入时设置过期时间")
    void shouldAccumulateCountWithTtl() {
        store = new RedisEphemeralStore(template);
        String key = uniqueKey();

        assertThat(store.increment(key, Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(store.increment(key, Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(template.getExpire(key)).isPositive();
    }

    @Test
    @DisplayName("一次性占位：首次成功，重复失败")
    void shouldClaimKeyOnlyOnce() {
        store = new RedisEphemeralStore(template);
        String key = uniqueKey();

        assertThat(store.markUsedOnce(key, Duration.ofMinutes(10))).isTrue();
        assertThat(store.markUsedOnce(key, Duration.ofMinutes(10))).isFalse();
    }

    private static String uniqueKey() {
        return "ql:test:ephemeral:" + UUID.randomUUID();
    }

    private static String redisHost() {
        return Optional.ofNullable(System.getenv("QINGLAN_REDIS_HOST")).orElse("localhost");
    }

    private static int redisPort() {
        return Integer.parseInt(Optional.ofNullable(System.getenv("QINGLAN_REDIS_PORT")).orElse("6379"));
    }
}
