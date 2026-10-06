package com.qinglan.user.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.crypto.CryptoService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CursorPage;
import com.qinglan.user.dto.AdminUserQuery;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserStatus;
import com.qinglan.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
    /** 游标段数：注册毫秒 : 用户 id */
    private static final int CURSOR_SEGMENTS = 2;

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

    private AdminUserCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new AdminUserCursor(null, null);
        }
        String[] parts = raw.split(KEY_SEPARATOR);
        if (parts.length != CURSOR_SEGMENTS) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
        try {
            return new AdminUserCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }

    private String formatCursor(User last) {
        return last.getCreatedAt().toEpochMilli() + KEY_SEPARATOR + last.getId();
    }

    /** 用户列表游标：与 {@code UserRepository#findAdminPage} 的排序键逐一对应 */
    private record AdminUserCursor(Instant createdAt, Long id) {
    }
}
