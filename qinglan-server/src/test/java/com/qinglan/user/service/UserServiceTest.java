package com.qinglan.user.service;

import com.qinglan.common.crypto.CryptoProperties;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.review.entity.AuditAction;
import com.qinglan.review.entity.AuditTargetType;
import com.qinglan.review.service.AuditLogService;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户治理服务的单元测试，重点覆盖不变量 #3（身份明文不出库）与 #7（管理动作必留痕）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_AES_KEY = "cWluZ2xhbi10ZXN0LWFlcy1rZXktMzItYnl0ZXMhISE=";
    private static final String STUDENT_ID = "2023010101";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private CryptoService cryptoService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService(new CryptoProperties(VALID_AES_KEY, "qinglan-test-pepper"));
        userService = new UserService(userRepository, cryptoService, auditLogService);
    }

    @Test
    @DisplayName("不变量 #7：封禁用户必须写入审计日志")
    void shouldWriteAuditLogWhenBanning() {
        User user = User.register("emailHash", "studentIdHash", "enc", "salt");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.ban(1L, Duration.ofDays(7), "人身攻击", 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(user.isBannedAt(Instant.now())).isTrue();
        verify(auditLogService).record(AuditTargetType.USER, 1L, AuditAction.BAN, 99L, "人身攻击");
    }

    @Test
    @DisplayName("不变量 #7：调整信用分必须写入审计日志，且分数被限制在 0-100")
    void shouldClampCreditAndWriteAuditLog() {
        User user = User.register("emailHash", "studentIdHash", "enc", "salt");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.adjustCredit(1L, -30, "违规发帖", 99L);
        assertThat(user.getCreditScore()).isEqualTo(70);

        userService.adjustCredit(1L, -100, "严重违规", 99L);
        assertThat(user.getCreditScore()).isZero();

        verify(auditLogService).record(AuditTargetType.USER, 1L, AuditAction.CREDIT_ADJUST, 99L, "违规发帖");
        verify(auditLogService).record(AuditTargetType.USER, 1L, AuditAction.CREDIT_ADJUST, 99L, "严重违规");
    }

    @Test
    @DisplayName("不变量 #3：学号以密文入库，仅保留不可逆哈希用于唯一约束")
    void shouldStoreStudentIdEncrypted() {
        when(userRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(userRepository.existsByStudentIdHash(anyString())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createVerifiedStudent("emailHash", STUDENT_ID);

        assertThat(user.getStudentIdEnc()).isNotEqualTo(STUDENT_ID);
        assertThat(cryptoService.decrypt(user.getStudentIdEnc())).isEqualTo(STUDENT_ID);
        assertThat(user.getStudentIdHash()).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(STUDENT_ID);
        assertThat(user.getAnonymousSalt()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(user.getCreditScore()).isEqualTo(User.INITIAL_CREDIT_SCORE);
        assertThat(user.getRole().name()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("同一校园邮箱不可重复认证")
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByEmailHash("emailHash")).thenReturn(true);

        assertThatThrownBy(() -> userService.createVerifiedStudent("emailHash", STUDENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_BOUND);
    }

    @Test
    @DisplayName("一人一学号：同一学号不可重复绑定")
    void shouldRejectDuplicateStudentId() {
        when(userRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(userRepository.existsByStudentIdHash(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.createVerifiedStudent("emailHash", STUDENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.STUDENT_ID_ALREADY_BOUND);
    }

    @Test
    @DisplayName("查询不存在的用户返回 USER_NOT_FOUND")
    void shouldFailWhenUserMissing() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.requireById(404L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("不变量 #3：对外档案 DTO 不含任何邮箱/学号字段")
    void shouldNotExposeIdentityFieldsInProfile() {
        Set<String> componentNames = Arrays.stream(UserProfileResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(componentNames).containsExactlyInAnyOrder(
                "id", "role", "status", "creditScore", "bannedUntil", "createdAt");
    }
}
