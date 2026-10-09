package com.qinglan.auth.store;

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
 * Redis 验证码存储的真跑集成测试（R-36 遗留：此前本机无 Redis，这条路径从未真跑）。
 *
 * <p>仅当 {@code QINGLAN_REDIS_TEST=true} 时运行——CI 用 service container 起 Redis 并置该变量；
 * 本地无 Redis 时自动跳过（用环境变量条件跳过，不是 {@code @Disabled} 蒙混）。</p>
 */
@EnabledIfEnvironmentVariable(named = "QINGLAN_REDIS_TEST", matches = "true")
class RedisVerificationCodeStoreTest {

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    private RedisVerificationCodeStore store;

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
    @DisplayName("保存 / 读取 / 删除验证码")
    void shouldSaveFindAndRemoveCode() {
        store = new RedisVerificationCodeStore(template);
        String hash = uniqueHash();

        store.saveCode(hash, "654321", Duration.ofMinutes(5));
        assertThat(store.findCode(hash)).contains("654321");

        store.removeCode(hash);
        assertThat(store.findCode(hash)).isEmpty();
    }

    @Test
    @DisplayName("发送配额：首次成功，窗口内再次被拒")
    void shouldAcquireSendSlotOnlyOnce() {
        store = new RedisVerificationCodeStore(template);
        String hash = uniqueHash();

        assertThat(store.tryAcquireSendSlot(hash, Duration.ofSeconds(60))).isTrue();
        assertThat(store.tryAcquireSendSlot(hash, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    @DisplayName("尝试次数 TTL 自愈：计数键没有 TTL 时补设，避免用户被永久锁死")
    void shouldHealAttemptCounterTtl() {
        store = new RedisVerificationCodeStore(template);
        String hash = uniqueHash();
        String attemptKey = "ql:auth:attempt:" + hash;

        assertThat(store.incrementAttempts(hash, Duration.ofMinutes(5))).isEqualTo(1);
        assertThat(template.getExpire(attemptKey)).isPositive();

        // 模拟「INCR 之后、EXPIRE 之前进程中断」：计数键存在但无 TTL
        template.persist(attemptKey);
        assertThat(template.getExpire(attemptKey)).isEqualTo(-1L);

        assertThat(store.incrementAttempts(hash, Duration.ofMinutes(5))).isEqualTo(2);
        assertThat(template.getExpire(attemptKey)).isPositive();
    }

    @Test
    @DisplayName("删除验证码同时清掉尝试计数")
    void shouldRemoveAttemptsWithCode() {
        store = new RedisVerificationCodeStore(template);
        String hash = uniqueHash();
        String attemptKey = "ql:auth:attempt:" + hash;

        store.saveCode(hash, "111111", Duration.ofMinutes(5));
        store.incrementAttempts(hash, Duration.ofMinutes(5));

        store.removeCode(hash);

        assertThat(store.findCode(hash)).isEmpty();
        assertThat(template.hasKey(attemptKey)).isFalse();
    }

    private static String uniqueHash() {
        return "test-" + UUID.randomUUID();
    }

    private static String redisHost() {
        return Optional.ofNullable(System.getenv("QINGLAN_REDIS_HOST")).orElse("localhost");
    }

    private static int redisPort() {
        return Integer.parseInt(Optional.ofNullable(System.getenv("QINGLAN_REDIS_PORT")).orElse("6379"));
    }
}
