package com.qinglan.user.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.security.AdminPermission;
import com.qinglan.common.web.CsvWriter;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminMeResponse;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.AssignableRole;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户与账号治理（身份创建、信用分、封禁）。
 *
 * <p>身份字段的加解密集中在本服务，明文身份不会流出到其它模块。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int ANONYMOUS_SALT_BYTES = 16;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String KEY_SEPARATOR = ":";
    /** 游标段数：注册微秒 : 用户 id */
    private static final int CURSOR_SEGMENTS = 2;
    /** 委员会成员的角色集合（参与表决的人）：上限与名单查询都用它 */
    private static final List<UserRole> COMMITTEE_ROLES = List.of(UserRole.ADMIN, UserRole.REVIEWER);
    /** 导出动作写入审计时 target_id 的哨兵值（导出没有单一目标实体） */
    private static final long EXPORT_TARGET_ID = 0L;

    private final UserRepository userRepository;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public Optional<User> findByEmailHash(String emailHash) {
        return userRepository.findByEmailHash(emailHash);
    }

    @Transactional(readOnly = true)
    public User requireById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 写操作入口的统一前置校验：黑名单或封禁期内直接拒绝。
     *
     * <p>访问令牌是无状态的（签发后 2 小时内不会失效），封禁不可能靠登录时的检查兜住，
     * 因此每个写入口（发帖、评论、点赞、上传）都必须重新校验一次。</p>
     */
    @Transactional(readOnly = true)
    public User requireUsableById(Long userId) {
        User user = requireById(userId);
        assertUsable(user);
        return user;
    }

    /** 判定规则只此一处，供已持有实体的调用方（如登录）复用 */
    public void assertUsable(User user) {
        if (user.getStatus() == UserStatus.BLACKLISTED || user.isBannedAt(Instant.now())) {
            throw new BizException(ErrorCode.USER_BANNED);
        }
    }

    /**
     * 学号绑定并创建用户：学号加密存储，同时建立不可逆哈希以满足"一人一学号"唯一约束。
     */
    @Transactional
    public User createVerifiedStudent(String emailHash, String studentId) {
        String studentIdHash = cryptoService.hashStudentId(studentId);
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        if (userRepository.existsByStudentIdHash(studentIdHash)) {
            // 与「并发下唯一约束兜底」返回同一个通用码：否则可以拿它逐个试探学号是否已被绑定（T-12 I）
            throw new BizException(ErrorCode.REGISTRATION_CONFLICT);
        }
        User user = User.register(
                emailHash,
                studentIdHash,
                cryptoService.encrypt(studentId),
                generateAnonymousSalt()
        );
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // 并发下唯一约束兜底，避免同一邮箱/学号被重复注册
            throw new BizException(ErrorCode.REGISTRATION_CONFLICT);
        }
    }

    @Transactional
    public void ban(Long userId, Duration duration, String reason, Long operatorId) {
        User user = requireById(userId);
        // 唯一超管是后台的最后一道保险：封掉它就没有人能解锁，只能改库（P5 D4）
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED);
        }
        Instant until = Instant.now().plus(duration);
        user.banUntil(until);
        auditLogService.record(AuditTargetType.USER, userId, AuditAction.BAN, operatorId, reason);
        log.info("封禁用户 userId={} 至 {}，操作人 operatorId={}", userId, until, operatorId);
    }

    @Transactional
    public void unban(Long userId, String reason, Long operatorId) {
        User user = requireById(userId);
        user.unban();
        auditLogService.record(AuditTargetType.USER, userId, AuditAction.UNBAN, operatorId, reason);
        log.info("解除封禁 userId={}，操作人 operatorId={}", userId, operatorId);
    }

    /**
     * 下放 / 收回 / 互转管理角色（P5 设计 7.3）。
     *
     * <p>四条规则，缺一条都会出事故：</p>
     * <ol>
     *   <li><b>操作人必须是可用的超管</b>——角色判断由端点注解收口（{@code ROLE_GRANT} 只有超管有），
     *       这里只按写入口的统一约定复查账号可用性；</li>
     *   <li><b>不能改自己</b>——否则超管可以把自己降级，直接锁死后台；</li>
     *   <li><b>不能动超管</b>——超管唯一且是初始账号，被降级同样等于锁死；</li>
     *   <li><b>新增委员受上限约束</b>——授予前先锁超管行再计数，避免并发把第 7 个人放进来。
     *       互转（ADMIN ↔ REVIEWER）不改变在册人数，因此不受上限影响。</li>
     * </ol>
     *
     * <p>幂等：目标角色与现状相同则直接返回，不写审计——否则反复点同一个按钮会刷出一串无意义的
     * 「授权」记录，把真正的变更加淹没（与 {@code ReviewService.restore} 的幂等同一取向）。</p>
     */
    @Transactional
    public void changeRole(Long targetId, AssignableRole newRole, String reason, Long operatorId) {
        requireUsableById(operatorId);
        if (Objects.equals(targetId, operatorId)) {
            throw new BizException(ErrorCode.ROLE_SELF_CHANGE_FORBIDDEN);
        }
        User target = requireById(targetId);
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new BizException(ErrorCode.SUPER_ADMIN_PROTECTED);
        }
        UserRole targetRole = newRole.toUserRole();
        if (target.getRole() == targetRole) {
            return;
        }
        boolean addedToCommittee = newRole.isCommittee() && !target.getRole().isCommittee();
        if (addedToCommittee) {
            lockCommitteeMutex();
            if (userRepository.countByRoleIn(COMMITTEE_ROLES) >= UserRole.MAX_COMMITTEE_SIZE) {
                throw new BizException(ErrorCode.COMMITTEE_FULL);
            }
        }
        UserRole previous = target.getRole();
        target.changeRole(targetRole);
        auditLogService.record(AuditTargetType.USER, targetId,
                newRole.isCommittee() ? AuditAction.ROLE_GRANT : AuditAction.ROLE_REVOKE,
                operatorId, describeRoleChange(previous, targetRole, reason));
        log.info("变更用户角色 userId={} {} → {}，操作人 operatorId={}", targetId, previous, targetRole, operatorId);
    }

    /**
     * 在册委员会人数（管理员 + 评议员）。
     *
     * <p>P5 二期：提案创建时用它做表决分母快照（{@code snapshot_size}），之后不再变化——
     * 用计数而非 {@link #listCommittee()} 的 size，避免为了一个数把整张名单读出来。</p>
     */
    @Transactional(readOnly = true)
    public long countCommittee() {
        return userRepository.countByRoleIn(COMMITTEE_ROLES);
    }

    /**
     * 委员会成员（管理员 + 评议员），按加入时间升序。
     *
     * <p>不外泄身份：{@link UserProfileResponse} 只有 id / 角色 / 状态 / 信用分 / 时间，
     * 与不变量 #3 一致（管理员本来就看不清真实身份，授权也只能按 userId 操作）。</p>
     */
    @Transactional(readOnly = true)
    public List<UserProfileResponse> listCommittee() {
        return userRepository.findByRoleInOrderByCreatedAtAsc(COMMITTEE_ROLES).stream()
                .map(this::toProfile)
                .toList();
    }

    /**
     * 以唯一超管行为互斥锁，把「数人数 → 授予」变成临界区。
     *
     * <p>拿不到那一行就显式失败，而不是静默跳过锁：超管唯一是种子保证的不变量，
     * 若它不成立（例如被人为改库），互斥锁会悄悄失效、上限随之可被绕过——宁可在授予时报错。</p>
     */
    private void lockCommitteeMutex() {
        List<User> superAdmins = userRepository.findForUpdate(UserRole.SUPER_ADMIN);
        if (superAdmins.size() != 1) {
            throw new IllegalStateException(
                    "超级管理员应唯一，实际 %d 个，无法校验委员会上限".formatted(superAdmins.size()));
        }
    }

    private static String describeRoleChange(UserRole from, UserRole to, String reason) {
        return "%s → %s：%s".formatted(from, to, reason == null ? "" : reason.trim());
    }

    @Transactional
    public void adjustCredit(Long userId, int delta, String reason, Long operatorId) {
        User user = requireById(userId);
        user.adjustCredit(delta);
        auditLogService.record(AuditTargetType.USER, userId, AuditAction.CREDIT_ADJUST, operatorId, reason);
        log.info("调整信用分 userId={} delta={} 结果={}，操作人 operatorId={}",
                userId, delta, user.getCreditScore(), operatorId);
    }

    /**
     * Entity → DTO 转换统一在 Service 层完成（AGENTS.md 模块边界）。
     */
    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getRole().name(),
                user.getStatus(),
                user.getCreditScore(),
                user.getBannedUntil(),
                user.getCreatedAt()
        );
    }

    /**
     * 当前管理端身份：角色 + 权限点集合（P5 设计 7.2）。
     *
     * <p>角色取自**数据库**而非令牌：令牌在签发时固化，被收回或降级后旧令牌仍带旧角色，
     * 而前端要显示与渲染的是「现在是什么」。权限点由 {@link UserRole#permissions()} 派生，
     * 排序后返回，便于前端与测试做稳定断言。</p>
     */
    @Transactional(readOnly = true)
    public AdminMeResponse currentAdminIdentity(Long userId) {
        User user = requireById(userId);
        List<String> permissions = user.getRole().permissions().stream()
                .map(AdminPermission::name)
                .sorted()
                .toList();
        return new AdminMeResponse(user.getId(), user.getRole().name(), permissions);
    }

    private String generateAnonymousSalt() {
        byte[] salt = new byte[ANONYMOUS_SALT_BYTES];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * 管理端用户列表：键集分页，多取一条判断是否还有下一页，避免额外 count 查询。
     *
     * <p>不提供身份维度检索（理由见 {@code UserRepository#findAdminPage}）。</p>
     */
    @Transactional(readOnly = true)
    public CursorPage<UserProfileResponse> listUsers(AdminUserQuery query) {
        AdminUserCursor cursor = parseCursor(query.cursor());
        List<User> rows = userRepository.findAdminPage(
                query.status(), query.role(), query.minCredit(), query.maxCredit(),
                query.createdFrom(), query.createdTo(),
                cursor.createdAt(), cursor.id(),
                PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<User> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        List<UserProfileResponse> items = page.stream().map(this::toProfile).toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * 导出前置校验：超过 {@link CsvWriter#DEFAULT_ROW_LIMIT} 直接抛
     * {@link ErrorCode#EXPORT_ROW_LIMIT_EXCEEDED}，明确报错而非静默截断。
     *
     * <p><b>必须在触碰响应之前调用</b>：导出控制器会先把 {@code Content-Type} 设为 {@code text/csv}，
     * 若此时才抛错，全局异常处理无法把 JSON 错误体写进一个已预设为 CSV 的响应（会以
     * {@code HttpMessageNotWritableException} 收场，客户端拿到 500 而不是 400）。</p>
     */
    @Transactional(readOnly = true)
    public void requireExportable(AdminUserQuery query) {
        long total = userRepository.countAdminFiltered(
                query.status(), query.role(), query.minCredit(), query.maxCredit(),
                query.createdFrom(), query.createdTo());
        if (total > CsvWriter.DEFAULT_ROW_LIMIT) {
            throw new BizException(ErrorCode.EXPORT_ROW_LIMIT_EXCEEDED);
        }
    }

    /**
     * 按当前筛选条件流式导出用户列表 CSV（阶段 B）。调用方应先经 {@link #requireExportable} 校验。
     *
     * <p>字段集沿用 {@link UserProfileResponse}——<b>不含学号 / 邮箱</b>（不变量 #3 在导出上同样不破例）。
     * 导出写一条 {@code DATA_EXPORT} 审计（不变量 #7 的意图范围），reason 只记筛选条件摘要与行数，
     * 不含身份明文。抓取条数已被前置校验约束在上限内。</p>
     */
    @Transactional
    public void exportCsv(AdminUserQuery query, Long operatorId, OutputStream out) throws IOException {
        List<User> rows = userRepository.findAdminPage(
                query.status(), query.role(), query.minCredit(), query.maxCredit(),
                query.createdFrom(), query.createdTo(), null, null,
                PageRequest.of(0, CsvWriter.DEFAULT_ROW_LIMIT));
        auditLogService.record(AuditTargetType.EXPORT, EXPORT_TARGET_ID, AuditAction.DATA_EXPORT, operatorId,
                "用户列表导出：status=%s role=%s minCredit=%s maxCredit=%s createdFrom=%s createdTo=%s rows=%d"
                        .formatted(query.status(), query.role(), query.minCredit(), query.maxCredit(),
                                query.createdFrom(), query.createdTo(), rows.size()));
        writeCsv(rows, out);
    }

    private void writeCsv(List<User> rows, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(CsvWriter.UTF8_BOM);
        writer.write(CsvWriter.line("id", "role", "status", "creditScore", "bannedUntil", "createdAt"));
        writer.write("\n");
        for (User user : rows) {
            writer.write(CsvWriter.line(
                    user.getId(),
                    user.getRole().name(),
                    user.getStatus(),
                    user.getCreditScore(),
                    user.getBannedUntil(),
                    user.getCreatedAt()));
            writer.write("\n");
        }
        writer.flush();
    }

    private AdminUserCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AdminUserCursor(null, null);
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            return new AdminUserCursor(CursorCodec.decode(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(User last) {
        return CursorCodec.encode(last.getCreatedAt()) + KEY_SEPARATOR + last.getId();
    }

    /** 用户列表游标：与 {@code UserRepository#findAdminPage} 的排序键逐一对应 */
    private record AdminUserCursor(Instant createdAt, Long id) {
    }
}
