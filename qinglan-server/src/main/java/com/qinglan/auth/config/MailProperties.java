package com.qinglan.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码邮件通道配置。
 *
 * <p>默认 {@code transport=logging}（只打日志，本地/CI 不配 SMTP 时行为不变）；
 * {@code transport=smtp} 时启用真实发信（{@code SmtpVerificationMailSender}）。</p>
 *
 * @param transport 投递通道：{@code logging}（默认）/ {@code smtp}
 * @param host      SMTP 主机（smtp 时必填）
 * @param port      SMTP 端口，缺失时默认 587（提交端口）
 * @param username  SMTP 用户名，可空（内网无认证时留空）
 * @param password  SMTP 密码，<b>只允许环境变量注入</b>，禁止写死进配置（AGENTS.md 第九章）
 * @param from      发件人地址，缺失时默认 {@code no-reply@qinglan.edu.cn}
 * @param tls       是否启用 STARTTLS，缺失时默认 true
 */
@ConfigurationProperties(prefix = "qinglan.auth.mail")
public record MailProperties(
        String transport,
        String host,
        int port,
        String username,
        String password,
        String from,
        Boolean tls
) {

    private static final String DEFAULT_TRANSPORT = "logging";
    private static final String DEFAULT_FROM = "no-reply@qinglan.edu.cn";

    /**
     * 归一化缺省值。记录用构造器绑定，缺失项为 null / 0，这里统一补齐，
     * 免得每个使用点都判空。
     */
    public MailProperties {
        if (transport == null || transport.isBlank()) {
            transport = DEFAULT_TRANSPORT;
        }
        if (port <= 0) {
            port = 587;
        }
        if (from == null || from.isBlank()) {
            from = DEFAULT_FROM;
        }
        if (tls == null) {
            tls = Boolean.TRUE;
        }
    }
}
