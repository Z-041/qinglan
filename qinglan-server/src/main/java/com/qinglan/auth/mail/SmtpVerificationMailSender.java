package com.qinglan.auth.mail;

import com.qinglan.auth.config.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

/**
 * 真实 SMTP 投递实现。
 *
 * <p>仅在 {@code qinglan.auth.mail.transport=smtp} 时创建；未配置时由
 * {@link LoggingVerificationMailSender} 兜底（默认 logging），本地/CI 行为不变。</p>
 *
 * <p>安全：密码只从配置（环境变量）读入，不进日志；验证码本身也不写日志。</p>
 */
@Component
@ConditionalOnProperty(prefix = "qinglan.auth.mail", name = "transport", havingValue = "smtp")
public class SmtpVerificationMailSender implements VerificationMailSender {

    private static final String SUBJECT = "【青栏】校园邮箱验证码";

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpVerificationMailSender(MailProperties properties) {
        if (properties.host() == null || properties.host().isBlank()) {
            throw new IllegalStateException(
                    "qinglan.auth.mail.transport=smtp 必须同时配置 qinglan.auth.mail.host，当前为空");
        }
        this.from = properties.from();
        this.mailSender = buildMailSender(properties);
    }

    private JavaMailSenderImpl buildMailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setDefaultEncoding("UTF-8");
        if (properties.username() != null && !properties.username().isBlank()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
        }
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.transport.protocol", "smtp");
        javaMail.put("mail.smtp.auth", String.valueOf(properties.username() != null && !properties.username().isBlank()));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.tls()));
        javaMail.put("mail.smtp.connectiontimeout", "5000");
        javaMail.put("mail.smtp.timeout", "5000");
        return sender;
    }

    @Override
    public void sendVerificationCode(String email, String code, Duration ttl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText("你的校园邮箱验证码是 " + code + "，"
                + ttl.toSeconds() + " 秒内有效。若非本人操作请忽略本邮件。");
        mailSender.send(message);
    }
}
