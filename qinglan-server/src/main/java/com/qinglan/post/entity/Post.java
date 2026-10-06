package com.qinglan.post.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 主帖。
 *
 * <p>两条硬约束写在这里，不指望调用方自觉：</p>
 * <ul>
 *   <li>状态只能经 {@link #transitionTo(ReviewState)} 变更，由状态机裁决合法性（不变量 #5）</li>
 *   <li>联系方式是否可见由 {@link #contactVisibleTo(Long, boolean)} 判定，脱敏在服务端完成（不变量 #2）</li>
 * </ul>
 *
 * <p>不提供 setter，避免绕过上述规则。</p>
 */
@Getter
@Entity
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    /**
     * 插入时的占位代号。真实代号 = {@code hash(post_id + author_id + salt)}，依赖主键，
     * 只能在 INSERT 拿到 id 之后回填（不变量 #1）。列是 NOT NULL，故需要一个插入期取值；
     * 回填与插入在同一事务内完成，中间态不会对外可见。
     */
    private static final String PLACEHOLDER_ANONYMOUS_CODE = "PENDING";

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /** 独立于正文存储，便于统一脱敏与批量隐藏（设计方案 4.3） */
    @Column(name = "contact", length = 200)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_visibility", nullable = false, length = 20)
    private ContactVisibility contactVisibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    /** anonymous_code = hash(post_id + author_id + salt)，同一作者跨帖代号必然不同（不变量 #1） */
    @Column(name = "anonymous_code", nullable = false, length = 32)
    private String anonymousCode;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "review_by")
    private Long reviewBy;

    @Column(name = "review_at")
    private Instant reviewAt;

    @Column(name = "taken_down_at")
    private Instant takenDownAt;

    @Column(name = "report_count", nullable = false)
    private int reportCount;

    /** 可见评论数，由 comment 模块在发布/删除评论时同事务维护（冗余以避免列表 N+1） */
    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    /**
     * 新建帖子：先落为草稿，再由 {@link #submit(RiskLevel)} 提交审核——
     * 这样「发布」本身就是状态机里的一条合法迁移，而不是绕过状态机直接写初值。
     */
    public static Post draft(Long authorId, Long topicId, String content, String contact) {
        Post post = new Post();
        post.authorId = authorId;
        post.topicId = topicId;
        post.content = content;
        post.contact = contact;
        post.contactVisibility = ContactVisibility.VISIBLE;
        post.status = new ReviewState.Draft().status();
        post.anonymousCode = PLACEHOLDER_ANONYMOUS_CODE;
        post.reportCount = 0;
        post.riskScore = 0;
        return post;
    }

    /**
     * 写入匿名代号。代号依赖 post_id，只能在插入拿到主键之后回填。
     */
    public void assignAnonymousCode(String anonymousCode) {
        this.anonymousCode = anonymousCode;
    }

    /** 提交审核：Draft → Pending（主帖先审后发，设计方案第一章红线 2） */
    public void submit(RiskLevel risk) {
        transitionTo(new ReviewState.Pending(Instant.now(), risk));
    }

    /**
     * 状态迁移唯一入口，非法迁移直接抛错（不变量 #5：禁止跳态）。
     */
    public void transitionTo(ReviewState next) {
        ReviewState.assertTransition(this.status, next.status());
        this.status = next.status();
        switch (next) {
            case ReviewState.Reviewing reviewing -> {
                this.reviewBy = reviewing.reviewerId();
                this.reviewAt = reviewing.claimedAt();
            }
            case ReviewState.Approved approved -> {
                if (approved.reviewerId() != null) {
                    this.reviewBy = approved.reviewerId();
                }
                this.reviewAt = approved.approvedAt();
                this.takenDownAt = null;
                // 放行/恢复后处罚理由不再成立，必须清空：否则申诉通过的帖子会一直挂着下架说明。
                // 历史仍完整保留在 review_log（不变量 #7 只增不改删）。
                this.reviewReason = null;
            }
            case ReviewState.Rejected rejected -> {
                this.reviewReason = rejected.explanation();
                this.reviewAt = Instant.now();
            }
            case ReviewState.TakenDown takenDown -> {
                this.reviewBy = takenDown.operatorId();
                this.reviewReason = takenDown.reason();
                this.takenDownAt = takenDown.at();
            }
            // Pending / Draft 不携带需要落库的额外字段
            case ReviewState.Pending ignored -> {
            }
            case ReviewState.Draft ignored -> {
            }
        }
    }

    public void applyRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    /**
     * 帖子的可见性：审核通过后对所有人可见，其余状态只有作者本人能看到
     * （设计方案：下架/驳回后作者可见、他人不可见）。
     *
     * @param viewerId 当前访问者，游客传 null
     */
    public boolean canBeViewedBy(Long viewerId) {
        return status == PostStatus.APPROVED || isAuthoredBy(viewerId);
    }

    public boolean isAuthoredBy(Long userId) {
        return userId != null && Objects.equals(this.authorId, userId);
    }

    /**
     * 处置动作：隐藏联系方式（仅作者本人可见）。
     *
     * <p>只改可见性标记、不清空 {@code contact} 值——处罚可能被申诉撤销，清空就找不回了。
     * 是否对外返回仍由 {@link #contactVisibleTo} 统一裁决（不变量 #2）。</p>
     */
    public void hideContact() {
        this.contactVisibility = ContactVisibility.HIDDEN;
    }

    /** 恢复联系方式可见性（申诉通过后） */
    public void showContact() {
        this.contactVisibility = ContactVisibility.VISIBLE;
    }

    /**
     * 联系方式可见性（不变量 #2）。未认证 viewer 一律 false——脱敏由服务端完成，
     * 不是前端 v-if 隐藏。
     *
     * @param viewerId            当前访问者，游客传 null
     * @param viewerAuthenticated 访问者是否已认证
     */
    public boolean contactVisibleTo(Long viewerId, boolean viewerAuthenticated) {
        if (contact == null || contact.isBlank()) {
            return false;
        }
        if (contactVisibility == ContactVisibility.HIDDEN) {
            // 被处罚隐藏后仅作者本人可见
            return isAuthoredBy(viewerId);
        }
        return viewerAuthenticated;
    }
}
