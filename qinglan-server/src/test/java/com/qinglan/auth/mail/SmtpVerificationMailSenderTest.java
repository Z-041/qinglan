package com.qinglan.auth.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.qinglan.auth.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SMTP 通道：用内嵌 GreenMail 起真实 SMTP 服务，真发一封信并校验收件人 / 主题 / 正文验证码。
 *
 * <p>无需 Docker——GreenMail 是纯 Java 内嵌服务器，本机与 CI 都能真跑。</p>
 */
class SmtpVerificationMailSenderTest {

    private GreenMail greenMail;

    @BeforeEach
    void startSmtpServer() {
        // 动态端口（0），避免与本机其它服务抢端口
        greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        greenMail.start();
    }

    @AfterEach
    void stopSmtpServer() {
        greenMail.stop();
    }

    @Test
    @DisplayName("SMTP 通道真发一封验证码邮件：收件人 / 主题 / 正文均正确")
    void shouldDeliverVerificationCode() throws Exception {
        SmtpVerificationMailSender sender = new SmtpVerificationMailSender(smtpProperties());

        sender.sendVerificationCode("student@qinglan.edu.cn", "123456", Duration.ofMinutes(5));

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage message = received[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("student@qinglan.edu.cn");
        assertThat(message.getSubject()).contains("验证码");
        // 正文为 UTF-8 中文，投递时可能被 base64 编码；getContent() 会按传输编码解码
        assertThat((String) message.getContent()).contains("123456");
        assertThat(message.getFrom()[0].toString()).contains("no-reply@qinglan.edu.cn");
    }

    @Test
    @DisplayName("transport=smtp 但缺 host 时构造即失败（fail-fast，不静默降级）")
    void shouldFailFastWhenHostMissing() {
        assertThatThrownBy(() -> new SmtpVerificationMailSender(
                new MailProperties("smtp", "", 587, null, null, "no-reply@qinglan.edu.cn", Boolean.TRUE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qinglan.auth.mail.host");
    }

    private MailProperties smtpProperties() {
        return new MailProperties("smtp", "127.0.0.1", greenMail.getSmtp().getPort(),
                null, null, "no-reply@qinglan.edu.cn", Boolean.FALSE);
    }
}
