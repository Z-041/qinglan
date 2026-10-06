package com.qinglan.auth.service;

import com.qinglan.auth.config.AuthProperties;
import com.qinglan.auth.dto.SendCodeResponse;
import com.qinglan.auth.mail.VerificationMailSender;
import com.qinglan.auth.store.InMemoryVerificationCodeStore;
import com.qinglan.auth.store.VerificationCodeStore;
import com.qinglan.common.crypto.CryptoProperties;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 校园邮箱验证码的发送、校验、限频与失败计数。
 */
class EmailVerificationServiceTest {

    private static final String AES_KEY_BASE64 = "cWluZ2xhbi10ZXN0LWFlcy1rZXktMzItYnl0ZXMhISE=";
    private static final String CAMPUS_DOMAIN = "qinglan.edu.cn";

    private VerificationCodeStore codeStore;
    private VerificationMailSender mailSender;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        codeStore = new InMemoryVerificationCodeStore();
        mailSender = mock(VerificationMailSender.class);
        CryptoService cryptoService = new CryptoService(new CryptoProperties(AES_KEY_BASE64, "qinglan-test-pepper"));
        AuthProperties authProperties = new AuthProperties(
                "memory",
                Duration.ofMinutes(5),
                6,
                5,
                Duration.ofSeconds(60),
                List.of(CAMPUS_DOMAIN),
                true
        );
        emailVerificationService = new EmailVerificationService(codeStore, mailSender, cryptoService, authProperties);
    }

    @Test
    @DisplayName("非校园邮箱域名被拒绝，且不发送验证码")
    void shouldRejectNonCampusEmail() {
        assertThatThrownBy(() -> emailVerificationService.sendCode("student@other.com"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);

        verify(mailSender, org.mockito.Mockito.never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("域名白名单未配置时拒绝全部邮箱（fail-closed）")
    void shouldRejectAllWhenWhitelistMissing() {
        AuthProperties emptyWhitelist = new AuthProperties(
                "memory", Duration.ofMinutes(5), 6, 5, Duration.ofSeconds(60), List.of(""), true);
        EmailVerificationService service = new EmailVerificationService(
                codeStore, mailSender,
                new CryptoService(new CryptoProperties(AES_KEY_BASE64, "qinglan-test-pepper")),
                emptyWhitelist);

        assertThatThrownBy(() -> service.sendCode("student@" + CAMPUS_DOMAIN))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
    }

    @Test
    @DisplayName("I：验证码校验成功即一次性失效，再次使用只返回统一错误码（不区分已失效/填错）")
    void shouldVerifyCodeOnceAndInvalidateIt() {
        String email = "once@qinglan.edu.cn";
        SendCodeResponse response = emailVerificationService.sendCode(email);

        String emailHash = emailVerificationService.verifyCode(email, response.devCode());
        assertThat(emailHash).hasSize(64);

        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, response.devCode()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_INVALID);
    }

    @Test
    @DisplayName("验证码错误累计到上限后必须重新获取")
    void shouldLockAfterTooManyWrongAttempts() {
        String email = "attempts@qinglan.edu.cn";
        emailVerificationService.sendCode(email);
        String wrongCode = "000000";

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatThrownBy(() -> emailVerificationService.verifyCode(email, wrongCode))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, wrongCode))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VERIFY_ATTEMPTS_EXCEEDED);

        assertThat(codeStore.findCode(new CryptoService(new CryptoProperties(AES_KEY_BASE64, "qinglan-test-pepper"))
                .hashEmail(email))).isEmpty();
    }

    @Test
    @DisplayName("同一邮箱在发送间隔内重复请求被拒绝")
    void shouldThrottleRepeatedSends() {
        String email = "throttle@qinglan.edu.cn";
        emailVerificationService.sendCode(email);

        assertThatThrownBy(() -> emailVerificationService.sendCode(email))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CODE_SEND_TOO_FREQUENT);
    }

    @Test
    @DisplayName("验证码为配置位数，本地联调可回显")
    void shouldGenerateConfiguredLengthCode() {
        SendCodeResponse response = emailVerificationService.sendCode("length@qinglan.edu.cn");

        assertThat(response.devCode()).matches("\\d{6}");
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        verify(mailSender).sendVerificationCode(anyString(), anyString(), any());
    }
}
