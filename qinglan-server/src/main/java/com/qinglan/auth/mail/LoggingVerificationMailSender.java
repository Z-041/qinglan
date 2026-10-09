package com.qinglan.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 日志实现（默认通道）。
 *
 * <p>安全要求：日志中不得出现验证码、邮箱全文（AGENTS.md 第九章），
 * 本地联调需要看到验证码时，通过 qinglan.auth.expose-code 在响应体回显。</p>
 *
 * <p>与 {@link SmtpVerificationMailSender} 互斥：未配置 smtp 或显式 logging 时走本实现，
 * 保证本地/CI 不配 SMTP 也能启动。</p>
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.auth.mail", name = "transport", havingValue = "logging", matchIfMissing = true)
public class LoggingVerificationMailSender implements VerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationMailSender.class);

    @Override
    public void sendVerificationCode(String email, String code, Duration ttl) {
        log.info("校园邮箱验证码已生成，收件人={}，有效期={}秒", mask(email), ttl.toSeconds());
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
