package com.qinglan.auth.service;

import com.qinglan.auth.config.JwtProperties;
import com.qinglan.auth.dto.AuthTokenResponse;
import com.qinglan.auth.dto.VerifyEmailResponse;
import com.qinglan.auth.security.AccessTokenPayload;
import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 认证编排：新用户需绑学号、老用户直接登录、封禁用户拒绝、邮箱凭证校验。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String JWT_SECRET_BASE64 = "cWluZ2xhbi10ZXN0LWp3dC1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtMDAwMA==";
    private static final String CAMPUS_EMAIL = "student@qinglan.edu.cn";
    private static final String EMAIL_HASH = "a".repeat(64);

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private UserService userService;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(
                JWT_SECRET_BASE64, "qinglan", Duration.ofHours(2), Duration.ofMinutes(10)));
        authService = new AuthService(emailVerificationService, userService, jwtService);
    }

    @Test
    @DisplayName("新用户通过邮箱验证后返回 emailToken，且要求绑定学号")
    void shouldRequireStudentBindingForNewUser() {
        when(emailVerificationService.verifyCode(CAMPUS_EMAIL, "123456")).thenReturn(EMAIL_HASH);
        when(userService.findByEmailHash(EMAIL_HASH)).thenReturn(Optional.empty());

        VerifyEmailResponse response = authService.verifyEmail(CAMPUS_EMAIL, "123456");

        assertThat(response.needsStudentBinding()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.user()).isNull();
        assertThat(jwtService.parseEmailToken(response.emailToken())).contains(EMAIL_HASH);
    }

    @Test
    @DisplayName("老用户通过邮箱验证后直接签发访问令牌")
    void shouldIssueAccessTokenForExistingUser() {
        User user = existingUser(7L);
        when(emailVerificationService.verifyCode(CAMPUS_EMAIL, "123456")).thenReturn(EMAIL_HASH);
        when(userService.findByEmailHash(EMAIL_HASH)).thenReturn(Optional.of(user));
        when(userService.toProfile(user)).thenReturn(profileOf(user));

        VerifyEmailResponse response = authService.verifyEmail(CAMPUS_EMAIL, "123456");

        assertThat(response.needsStudentBinding()).isFalse();
        assertThat(response.emailToken()).isNull();
        assertThat(response.user().id()).isEqualTo(7L);
        Optional<AccessTokenPayload> payload = jwtService.parseAccessToken(response.accessToken());
        assertThat(payload).hasValueSatisfying(parsed -> {
            assertThat(parsed.userId()).isEqualTo(7L);
            assertThat(parsed.role()).isEqualTo(UserRole.STUDENT);
        });
    }

    @Test
    @DisplayName("被封禁用户即使邮箱验证通过也拒绝登录")
    void shouldRejectBannedUser() {
        User user = existingUser(7L);
        user.banUntil(Instant.now().plus(Duration.ofDays(3)));
        when(emailVerificationService.verifyCode(CAMPUS_EMAIL, "123456")).thenReturn(EMAIL_HASH);
        when(userService.findByEmailHash(EMAIL_HASH)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail(CAMPUS_EMAIL, "123456"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("邮箱认证凭证无效时拒绝绑定学号")
    void shouldRejectInvalidEmailToken() {
        assertThatThrownBy(() -> authService.bindStudentId("not-a-valid-token", "2023010101"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.EMAIL_TOKEN_INVALID);
    }

    @Test
    @DisplayName("绑定学号成功并签发访问令牌")
    void shouldBindStudentIdAndIssueToken() {
        User user = existingUser(7L);
        String emailToken = jwtService.issueEmailToken(EMAIL_HASH);
        when(userService.createVerifiedStudent(EMAIL_HASH, "2023010101")).thenReturn(user);
        when(userService.toProfile(user)).thenReturn(profileOf(user));

        AuthTokenResponse response = authService.bindStudentId(emailToken, "2023010101");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.user().id()).isEqualTo(7L);
        assertThat(jwtService.parseAccessToken(response.accessToken()))
                .hasValueSatisfying(parsed -> assertThat(parsed.userId()).isEqualTo(7L));
    }

    private User existingUser(Long id) {
        User user = User.register(EMAIL_HASH, "b".repeat(64), "encrypted", "c".repeat(32));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfileResponse profileOf(User user) {
        return new UserProfileResponse(
                user.getId(), user.getRole().name(), UserStatus.ACTIVE, user.getCreditScore(), null, Instant.now());
    }
}
