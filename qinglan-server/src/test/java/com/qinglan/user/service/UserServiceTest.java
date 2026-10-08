package com.qinglan.user.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.crypto.CryptoProperties;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.AssignableRole;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
    @DisplayName("I：学号被占用时返回与并发兜底相同的通用码，避免逐个试探学号是否已绑定")
    void shouldRejectDuplicateStudentIdWithGenericCode() {
        when(userRepository.existsByEmailHash(anyString())).thenReturn(false);
        when(userRepository.existsByStudentIdHash(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.createVerifiedStudent("emailHash", STUDENT_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.REGISTRATION_CONFLICT);
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
    @DisplayName("B：封禁期内写操作被拒——令牌无状态，封禁必须在每个写入口重查")
    void shouldRejectBannedUserOnWritePath() {
        User user = User.register("emailHash", "studentIdHash", "enc", "salt");
        user.banUntil(Instant.now().plus(Duration.ofDays(3)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.requireUsableById(1L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("B：黑名单用户即使封禁已过期也拒绝写操作")
    void shouldRejectBlacklistedUserOnWritePath() {
        User user = User.register("emailHash", "studentIdHash", "enc", "salt");
        ReflectionTestUtils.setField(user, "status", UserStatus.BLACKLISTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.requireUsableById(1L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    @DisplayName("B：正常账号通过写入口校验")
    void shouldAllowActiveUserOnWritePath() {
        User user = User.register("emailHash", "studentIdHash", "enc", "salt");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.requireUsableById(1L)).isSameAs(user);
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

    @Test
    @DisplayName("用户列表首页：多取一条判断还有下一页，返回 20 条并给出游标")
    void shouldListUsersFirstPage() {
        List<User> rows = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            rows.add(userWithId((long) (100 - i), "2026-01-01T00:00:00Z"));
        }
        when(userRepository.findAdminPage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rows);

        CursorPage<UserProfileResponse> page = userService.listUsers(
                new AdminUserQuery(null, null, null, null, null, null, null));

        assertThat(page.items()).hasSize(20);
        assertThat(page.nextCursor()).isEqualTo("1767225600000000:81");
    }

    @Test
    @DisplayName("用户列表末页：不足一页时 nextCursor 为 null")
    void shouldListUsersLastPage() {
        when(userRepository.findAdminPage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(userWithId(1L, "2026-01-01T00:00:00Z")));

        CursorPage<UserProfileResponse> page = userService.listUsers(
                new AdminUserQuery(null, null, null, null, null, null, null));

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("非法游标抛 CURSOR_INVALID")
    void shouldRejectMalformedUserCursor() {
        assertThatThrownBy(() -> userService.listUsers(
                new AdminUserQuery(null, null, null, null, null, null, "abc")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.CURSOR_INVALID);
    }

    @Test
    @DisplayName("游标被解析后原样传入仓储")
    void shouldPassParsedCursorToRepository() {
        when(userRepository.findAdminPage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        userService.listUsers(new AdminUserQuery(null, null, null, null, null, null, "1767225600000000:7"));

        verify(userRepository).findAdminPage(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(Instant.parse("2026-01-01T00:00:00Z")), eq(7L),
                eq(PageRequest.of(0, 21)));
    }

    private User userWithId(Long id, String isoInstant) {
        User user = User.register("hash-" + id, "student-hash-" + id, "enc", "a".repeat(32));
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(
                user, "createdAt", Instant.parse(isoInstant));
        return user;
    }

    @Test
    @DisplayName("游标带微秒：用户边界行与下一行同毫秒时，续页仍按边界行的原始时刻")
    void shouldKeepMicrosWhenUsersShareTheSameMillisecond() {
        List<User> rows = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            String createdAt = switch (i) {
                case 19 -> "2026-01-01T00:00:19.000234Z";
                case 20 -> "2026-01-01T00:00:19.000235Z";
                default -> "2026-01-01T00:00:%02dZ".formatted(i);
            };
            rows.add(userWithId((long) (100 - i), createdAt));
        }
        when(userRepository.findAdminPage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(rows);

        CursorPage<UserProfileResponse> page = userService.listUsers(
                new AdminUserQuery(null, null, null, null, null, null, null));

        assertThat(rows.get(19).getCreatedAt().toEpochMilli())
                .isEqualTo(rows.get(20).getCreatedAt().toEpochMilli());
        assertThat(page.nextCursor()).isEqualTo("1767225619000234:81");

        userService.listUsers(new AdminUserQuery(null, null, null, null, null, null, page.nextCursor()));

        verify(userRepository).findAdminPage(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(Instant.parse("2026-01-01T00:00:19.000234Z")), eq(81L), eq(PageRequest.of(0, 21)));
    }

    // ---------------------------------------------------------- P5 权限下放 / 收回

    private static final long TARGET_ID = 7L;
    private static final long OPERATOR_ID = 1L;

    @Test
    @DisplayName("下放管理员：角色变更并写 ROLE_GRANT 审计（理由里带上原角色）")
    void shouldGrantRoleAndRecordAudit() {
        User target = userWithRole(TARGET_ID, UserRole.STUDENT);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);
        when(userRepository.findForUpdate(UserRole.SUPER_ADMIN)).thenReturn(List.of(superAdmin));
        when(userRepository.countByRoleIn(any())).thenReturn(2L);

        userService.changeRole(TARGET_ID, AssignableRole.ADMIN, "缺人手", OPERATOR_ID);

        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        verify(auditLogService).record(AuditTargetType.USER, TARGET_ID, AuditAction.ROLE_GRANT,
                OPERATOR_ID, "STUDENT → ADMIN：缺人手");
    }

    @Test
    @DisplayName("收回权限：写 ROLE_REVOKE 审计，且不触发上限校验（人数只减不增）")
    void shouldRevokeRoleAndRecordAudit() {
        User target = userWithRole(TARGET_ID, UserRole.REVIEWER);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);

        userService.changeRole(TARGET_ID, AssignableRole.STUDENT, "任期结束", OPERATOR_ID);

        assertThat(target.getRole()).isEqualTo(UserRole.STUDENT);
        verify(auditLogService).record(AuditTargetType.USER, TARGET_ID, AuditAction.ROLE_REVOKE,
                OPERATOR_ID, "REVIEWER → STUDENT：任期结束");
        verify(userRepository, never()).findForUpdate(any());
    }

    @Test
    @DisplayName("委员会满员时新增第 7 人被拒：不落角色、不写审计")
    void shouldRejectGrantWhenCommitteeFull() {
        User target = userWithRole(TARGET_ID, UserRole.STUDENT);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);
        when(userRepository.findForUpdate(UserRole.SUPER_ADMIN)).thenReturn(List.of(superAdmin));
        when(userRepository.countByRoleIn(any())).thenReturn((long) UserRole.MAX_COMMITTEE_SIZE);

        assertThatThrownBy(() ->
                userService.changeRole(TARGET_ID, AssignableRole.REVIEWER, "再加一个", OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.COMMITTEE_FULL);

        assertThat(target.getRole()).isEqualTo(UserRole.STUDENT);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("委员会内部互转（REVIEWER → ADMIN）不占新名额，因此不查上限")
    void shouldAllowRoleSwapWithinCommitteeWithoutLimitCheck() {
        User target = userWithRole(TARGET_ID, UserRole.REVIEWER);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);

        userService.changeRole(TARGET_ID, AssignableRole.ADMIN, "升为管理员", OPERATOR_ID);

        assertThat(target.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository, never()).findForUpdate(any());
        verify(userRepository, never()).countByRoleIn(any());
    }

    @Test
    @DisplayName("超管不可被降级：唯一超管被改掉，后台就再也没人能解锁")
    void shouldProtectSuperAdminFromRoleChange() {
        User target = userWithRole(TARGET_ID, UserRole.SUPER_ADMIN);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);

        assertThatThrownBy(() -> userService.changeRole(TARGET_ID, AssignableRole.STUDENT, "换人", OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.SUPER_ADMIN_PROTECTED);
    }

    @Test
    @DisplayName("不能修改自己的角色")
    void shouldRejectSelfRoleChange() {
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(superAdmin));

        assertThatThrownBy(() ->
                userService.changeRole(OPERATOR_ID, AssignableRole.ADMIN, "自己改自己", OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ROLE_SELF_CHANGE_FORBIDDEN);
    }

    @Test
    @DisplayName("目标已是该角色时幂等：不写审计，避免反复点按钮刷出无意义的授权记录")
    void shouldSkipRoleChangeWhenAlreadyTarget() {
        User target = userWithRole(TARGET_ID, UserRole.ADMIN);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);

        userService.changeRole(TARGET_ID, AssignableRole.ADMIN, "重复点", OPERATOR_ID);

        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("超管不唯一时授予直接失败：互斥锁失效必须显式报错，不能静默放行")
    void shouldFailWhenSuperAdminIsNotUnique() {
        User target = userWithRole(TARGET_ID, UserRole.STUDENT);
        User superAdmin = userWithRole(OPERATOR_ID, UserRole.SUPER_ADMIN);
        stubOperatorAndTarget(superAdmin, target);
        when(userRepository.findForUpdate(UserRole.SUPER_ADMIN))
                .thenReturn(List.of(superAdmin, userWithRole(99L, UserRole.SUPER_ADMIN)));

        assertThatThrownBy(() -> userService.changeRole(TARGET_ID, AssignableRole.ADMIN, "授予", OPERATOR_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("封禁超管被拒，且不写审计（P5 D4）")
    void shouldProtectSuperAdminFromBan() {
        User target = userWithRole(TARGET_ID, UserRole.SUPER_ADMIN);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.ban(TARGET_ID, Duration.ofDays(7), "误操作", OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.SUPER_ADMIN_PROTECTED);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("委员会名单按加入时间返回管理员与评议员")
    void shouldListCommittee() {
        when(userRepository.findByRoleInOrderByCreatedAtAsc(List.of(UserRole.ADMIN, UserRole.REVIEWER)))
                .thenReturn(List.of(userWithRole(1L, UserRole.ADMIN), userWithRole(2L, UserRole.REVIEWER)));

        List<UserProfileResponse> committee = userService.listCommittee();

        assertThat(committee).extracting(UserProfileResponse::role)
                .containsExactly(UserRole.ADMIN.name(), UserRole.REVIEWER.name());
    }

    @Test
    @DisplayName("当前身份：角色取自数据库（不是令牌），权限点与角色矩阵一致且稳定排序")
    void shouldReturnCurrentIdentityFromDatabase() {
        User reviewer = userWithRole(TARGET_ID, UserRole.REVIEWER);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(reviewer));

        var identity = userService.currentAdminIdentity(TARGET_ID);

        assertThat(identity.userId()).isEqualTo(TARGET_ID);
        assertThat(identity.role()).isEqualTo(UserRole.REVIEWER.name());
        assertThat(identity.permissions())
                .containsExactlyInAnyOrderElementsOf(
                        UserRole.REVIEWER.permissions().stream().map(Enum::name).toList());
        assertThat(identity.permissions()).isSorted();
    }

    private void stubOperatorAndTarget(User operator, User target) {
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator));
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
    }

    private static User userWithRole(Long id, UserRole role) {
        User user = User.register("hash-" + id, "student-hash-" + id, "enc", "a".repeat(32));
        ReflectionTestUtils.setField(user, "id", id);
        user.changeRole(role);
        return user;
    }
}
