package com.qinglan.auth.mail;

import com.qinglan.auth.config.MailProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件通道选择：未配 SMTP 时走日志实现（默认，行为不变），配 transport=smtp 时才走真实 SMTP。
 *
 * <p>两者由 {@code @ConditionalOnProperty} 互斥，保证 IoC 容器里始终只有一个
 * {@link VerificationMailSender}。</p>
 */
class VerificationMailSenderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailScenario.class);

    @Test
    @DisplayName("未配置 transport 时使用日志实现（默认，本地/CI 行为不变）")
    void shouldDefaultToLoggingSender() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(VerificationMailSender.class);
            assertThat(context.getBean(VerificationMailSender.class))
                    .isInstanceOf(LoggingVerificationMailSender.class);
        });
    }

    @Test
    @DisplayName("transport=logging 同样使用日志实现")
    void shouldUseLoggingSenderWhenExplicitLogging() {
        runner.withPropertyValues("qinglan.auth.mail.transport=logging")
                .run(context -> assertThat(context.getBean(VerificationMailSender.class))
                        .isInstanceOf(LoggingVerificationMailSender.class));
    }

    @Test
    @DisplayName("transport=smtp 时切换到 SMTP 实现")
    void shouldUseSmtpSenderWhenConfigured() {
        runner.withPropertyValues(
                        "qinglan.auth.mail.transport=smtp",
                        "qinglan.auth.mail.host=127.0.0.1",
                        "qinglan.auth.mail.port=2525")
                .run(context -> assertThat(context.getBean(VerificationMailSender.class))
                        .isInstanceOf(SmtpVerificationMailSender.class));
    }

    @Configuration
    @ComponentScan(basePackageClasses = VerificationMailSender.class)
    @EnableConfigurationProperties(MailProperties.class)
    static class MailScenario {
    }
}
