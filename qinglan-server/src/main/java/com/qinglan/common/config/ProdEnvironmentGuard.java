package com.qinglan.common.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 生产环境启动守卫（fail-closed）。
 *
 * <p>profile 约定：{@code dev}（本地默认，不激活 profile）/ {@code test}（测试，见
 * {@code @ActiveProfiles("test")}）/ {@code prod}（生产，部署时显式
 * {@code SPRING_PROFILES_ACTIVE=prod}）。本地与 CI 都不会激活 {@code prod}，因此本守卫
 * 在开发态永不触发，不会打断本地开发。</p>
 *
 * <p><b>为什么用 {@link BeanFactoryPostProcessor} 而不是 {@code InitializingBean} / {@code @PostConstruct}</b>：
 * 后两者所属 bean 的初始化顺序不保证排在 DataSource / Flyway 之前，生产配置写错时可能先撞上
 * 数据库连接失败、把「配置错误」误报成「连不上库」。BeanFactoryPostProcessor 在<b>任何普通 bean
 * 实例化之前</b>执行，能保证生产配置校验最先跑、把可操作的中文原因直接打出来。</p>
 *
 * <p>校验项（任一不满足即启动失败）：</p>
 * <ul>
 *   <li>{@code qinglan.auth.expose-code} 必须为 false（生产不得在响应体回显验证码）</li>
 *   <li>{@code qinglan.auth.code-store} 与 {@code qinglan.store.type} 必须为 redis
 *       （memory 在多实例下不一致、重启丢验证码）</li>
 *   <li>{@code qinglan.storage.type} 必须为 s3（AGENTS.md 第九章：上传文件存对象存储，
 *       不存应用服务器本地磁盘；本地磁盘在多实例下不共享、且随容器重建丢失）</li>
 *   <li>{@code qinglan.storage.image-signing-secret} 必须显式配置（私有图片签名 URL 的密钥，
 *       不得依赖回落到 {@code qinglan.jwt.secret}）</li>
 *   <li>{@code spring.datasource.url} 不得指向本机；{@code ddl-auto} 不得为
 *       create / create-drop / update（表结构只能由 Flyway 管理）</li>
 * </ul>
 */
@Component
public class ProdEnvironmentGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    /** 生产 profile 名。激活它才会触发校验，故本地/测试行为不变 */
    static final String PROD_PROFILE = "prod";

    private static final Set<String> FORBIDDEN_DDL_AUTO = Set.of("create", "create-drop", "update");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }
        List<String> violations = collectViolations(environment);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "生产环境（profile=prod）配置校验失败，已拒绝启动：\n  - "
                            + String.join("\n  - ", violations)
                            + "\n请修正上述配置（取值与说明见 application.example.yml），修正后重启。");
        }
    }

    private List<String> collectViolations(Environment env) {
        List<String> violations = new ArrayList<>();

        if (env.getProperty("qinglan.auth.expose-code", Boolean.class, false)) {
            violations.add("qinglan.auth.expose-code=true：生产禁止在接口响应里回显验证码，"
                    + "请设 QINGLAN_AUTH_EXPOSE_CODE=false");
        }

        String codeStore = env.getProperty("qinglan.auth.code-store", "memory");
        if (!"redis".equalsIgnoreCase(codeStore)) {
            violations.add("qinglan.auth.code-store=" + codeStore + "：生产必须为 redis，"
                    + "memory 在多实例下验证码不共享、重启即丢，请设 QINGLAN_AUTH_CODE_STORE=redis");
        }

        String storeType = env.getProperty("qinglan.store.type", "memory");
        if (!"redis".equalsIgnoreCase(storeType)) {
            violations.add("qinglan.store.type=" + storeType + "：生产必须为 redis，"
                    + "memory 在多实例下计数/一次性令牌不共享，请设 QINGLAN_STORE_TYPE=redis");
        }

        String storageType = env.getProperty("qinglan.storage.type", "local");
        if (!"s3".equalsIgnoreCase(storageType)) {
            violations.add("qinglan.storage.type=" + storageType + "：生产必须为 s3（上传文件落对象存储），"
                    + "local 会把图片写进应用服务器本地磁盘、多实例不共享且随容器重建丢失，"
                    + "请设 QINGLAN_STORAGE_TYPE=s3");
        }

        // 私有图片签名密钥：留空时会回落到 qinglan.jwt.secret（仅本地/测试的权宜），生产不得依赖回落——
        // 二者用途不同、轮换影响面不同，混用会让「换 JWT 密钥」这种事外扩到图片链接的可用性
        String imageSigningSecret = env.getProperty("qinglan.storage.image-signing-secret", "");
        if (imageSigningSecret.isBlank()) {
            violations.add("qinglan.storage.image-signing-secret 未显式配置：生产不得依赖回落到 qinglan.jwt.secret，"
                    + "请设 QINGLAN_STORAGE_IMAGE_SIGNING_SECRET（生成参考：openssl rand -base64 48）");
        }

        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto", "");
        if (FORBIDDEN_DDL_AUTO.contains(ddlAuto.toLowerCase(Locale.ROOT))) {
            violations.add("spring.jpa.hibernate.ddl-auto=" + ddlAuto
                    + "：生产禁止 Hibernate 自动改表，表结构只能由 Flyway 管理，必须为 validate");
        }

        String dbUrl = env.getProperty("spring.datasource.url", "");
        if (pointsAtLocalhost(dbUrl)) {
            violations.add("spring.datasource.url 指向本机（" + dbUrl
                    + "）：生产必须指向真实数据库，请设 QINGLAN_DB_URL");
        }

        return violations;
    }

    private boolean pointsAtLocalhost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return false;
        }
        String lowered = jdbcUrl.toLowerCase(Locale.ROOT);
        return lowered.contains("//localhost") || lowered.contains("//127.0.0.1")
                || lowered.contains("//[::1]") || lowered.contains("//0.0.0.0");
    }
}
