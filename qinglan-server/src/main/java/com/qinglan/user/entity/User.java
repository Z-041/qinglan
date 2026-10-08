package com.qinglan.user.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 认证学生。
 *
 * <p>身份字段处理（AGENTS.md 不变量 #3）：表中不存邮箱与学号明文，
 * 邮箱存不可逆哈希用于登录查找，学号存 AES-GCM 密文 + 不可逆哈希用于"一人一学号"唯一约束。</p>
 *
 * <p>不提供任何 setter，状态变更必须走业务方法，避免绕过信用分与封禁规则。</p>
 */
@Getter
@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_email_hash", columnNames = "email_hash"),
                @UniqueConstraint(name = "uk_user_student_id_hash", columnNames = "student_id_hash")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    public static final int INITIAL_CREDIT_SCORE = 100;
    public static final int MIN_CREDIT_SCORE = 0;
    public static final int MAX_CREDIT_SCORE = 100;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "student_id_enc", nullable = false, length = 512)
    private String studentIdEnc;

    @Column(name = "student_id_hash", nullable = false, length = 64)
    private String studentIdHash;

    /** 匿名代号派生用盐，一人一值，泄露也无法反推作者 */
    @Column(name = "anonymous_salt", nullable = false, length = 64)
    private String anonymousSalt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "credit_score", nullable = false)
    private int creditScore;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    public static User register(String emailHash, String studentIdHash, String studentIdEnc, String anonymousSalt) {
        User user = new User();
        user.emailHash = emailHash;
        user.studentIdHash = studentIdHash;
        user.studentIdEnc = studentIdEnc;
        user.anonymousSalt = anonymousSalt;
        user.role = UserRole.STUDENT;
        user.status = UserStatus.ACTIVE;
        user.creditScore = INITIAL_CREDIT_SCORE;
        return user;
    }

    public void banUntil(Instant until) {
        this.status = UserStatus.BANNED;
        this.bannedUntil = until;
    }

    public void unban() {
        this.status = UserStatus.ACTIVE;
        this.bannedUntil = null;
    }

    public void adjustCredit(int delta) {
        this.creditScore = Math.max(MIN_CREDIT_SCORE, Math.min(MAX_CREDIT_SCORE, this.creditScore + delta));
    }

    /**
     * 变更角色（P5 权限下放 / 收回）。
     *
     * <p>与封禁一样走业务方法而不是暴露 setter：角色是安全资产，任何变更都必须能被审计追到
     * （由 {@code UserService#changeRole} 落 {@code ROLE_GRANT} / {@code ROLE_REVOKE}）。
     * 本方法只承担状态变更，不承担「谁有权改、上限是多少、超管能不能动」这些判断——
     * 那些在 Service 层。</p>
     */
    public void changeRole(UserRole role) {
        this.role = role;
    }

    public boolean isBannedAt(Instant moment) {
        return status == UserStatus.BANNED && bannedUntil != null && bannedUntil.isAfter(moment);
    }
}
