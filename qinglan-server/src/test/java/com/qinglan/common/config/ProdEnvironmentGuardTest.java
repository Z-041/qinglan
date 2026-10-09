package com.qinglan.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产启动守卫：只在 prod profile 激活时校验，且任一项不满足即启动失败。
 *
 * <p>本地/测试（不激活 prod）必须完全不受影响——这是「不打断本地开发」的设计要点。</p>
 */
class ProdEnvironmentGuardTest {

    @Test
    @DisplayName("prod 且配置合规时放行")
    void shouldPassWhenProdConfigurationIsComplete() {
        MockEnvironment env = prodEnvironment()
                .withProperty("qinglan.auth.expose-code", "false")
                .withProperty("qinglan.auth.code-store", "redis")
                .withProperty("qinglan.store.type", "redis")
                .withProperty("qinglan.storage.type", "s3")
                .withProperty("qinglan.storage.image-signing-secret", "prod-image-signing-secret-000000000000000000")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
                .withProperty("spring.datasource.url", "jdbc:mysql://db.internal:3306/qinglan");

        assertThatCode(() -> guard(env).postProcessBeanFactory(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod 下 expose-code=true 即拒绝启动，原因含该配置名")
    void shouldRejectWhenExposeCodeEnabled() {
        assertThatThrownBy(() -> guard(prodEnvironment().withProperty("qinglan.auth.expose-code", "true"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.auth.expose-code");
    }

    @Test
    @DisplayName("prod 下 code-store 非 redis 即拒绝启动")
    void shouldRejectWhenCodeStoreNotRedis() {
        assertThatThrownBy(() -> guard(prodEnvironment().withProperty("qinglan.auth.code-store", "memory"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.auth.code-store=memory");
    }

    @Test
    @DisplayName("prod 下 store.type 非 redis 即拒绝启动")
    void shouldRejectWhenEphemeralStoreNotRedis() {
        assertThatThrownBy(() -> guard(prodEnvironment().withProperty("qinglan.store.type", "memory"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.store.type=memory");
    }

    @Test
    @DisplayName("prod 下 storage.type 非 s3 即拒绝启动（图片必须落对象存储，不能落应用服务器本地磁盘）")
    void shouldRejectWhenStorageNotObjectStorage() {
        assertThatThrownBy(() -> guard(prodEnvironment().withProperty("qinglan.storage.type", "local"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.storage.type=local");
    }

    @Test
    @DisplayName("prod 下未显式配置图片签名密钥即拒绝启动（不得依赖回落到 jwt.secret）")
    void shouldRejectWhenImageSigningSecretMissing() {
        MockEnvironment env = prodEnvironment()
                .withProperty("qinglan.storage.type", "s3")
                .withProperty("qinglan.storage.image-signing-secret", "");

        assertThatThrownBy(() -> guard(env).postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.storage.image-signing-secret");
    }

    @Test
    @DisplayName("prod 下 ddl-auto=update 即拒绝启动（表结构只能由 Flyway 管理）")
    void shouldRejectWhenDdlAutoMutatesSchema() {
        assertThatThrownBy(() -> guard(prodEnvironment().withProperty("spring.jpa.hibernate.ddl-auto", "update"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddl-auto=update");
    }

    @Test
    @DisplayName("prod 下数据库指向本机即拒绝启动")
    void shouldRejectWhenDatabasePointsAtLocalhost() {
        assertThatThrownBy(() -> guard(prodEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/qinglan"))
                .postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("指向本机");
    }

    @Test
    @DisplayName("多条配置违规时全部列出，便于一次改完")
    void shouldListAllViolations() {
        MockEnvironment env = prodEnvironment()
                .withProperty("qinglan.auth.expose-code", "true")
                .withProperty("qinglan.auth.code-store", "memory")
                .withProperty("qinglan.store.type", "memory");

        assertThatThrownBy(() -> guard(env).postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expose-code")
                .hasMessageContaining("code-store")
                .hasMessageContaining("store.type");
    }

    @Test
    @DisplayName("未激活 prod（本地默认）时即便配置不合规也不拦——不打断本地开发")
    void shouldNotGuardOutsideProd() {
        MockEnvironment devEnv = new MockEnvironment()
                .withProperty("qinglan.auth.expose-code", "true")
                .withProperty("qinglan.auth.code-store", "memory");

        assertThatCode(() -> guard(devEnv).postProcessBeanFactory(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test profile 同样不受生产守卫影响")
    void shouldNotGuardInTestProfile() {
        MockEnvironment testEnv = new MockEnvironment()
                .withProperty("qinglan.auth.expose-code", "true")
                .withProperty("qinglan.auth.code-store", "memory");
        testEnv.addActiveProfile("test");

        assertThatCode(() -> guard(testEnv).postProcessBeanFactory(null)).doesNotThrowAnyException();
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.addActiveProfile("prod");
        return env;
    }

    private ProdEnvironmentGuard guard(MockEnvironment env) {
        ProdEnvironmentGuard guard = new ProdEnvironmentGuard();
        guard.setEnvironment(env);
        return guard;
    }
}
