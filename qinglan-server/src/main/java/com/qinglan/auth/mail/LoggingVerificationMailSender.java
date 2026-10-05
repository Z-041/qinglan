package com.qinglan.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 日志实现（P0 占位）。
 *
 * <p>安全要求：日志中不得出现验证码、邮箱全文（AGENTS.md 第九章），
 * 本地联调需要看到验证码时，通过 qinglan.auth.expose-code 在响应体回显。</p>
 */
@Component
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
