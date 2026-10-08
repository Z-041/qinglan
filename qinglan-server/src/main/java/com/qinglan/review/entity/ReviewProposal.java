package com.qinglan.review.entity;

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

/**
 * 处置提案：一条「对某目标执行某处置」的申请，由委员会表决（P5 二期）。
 *
 * <p>两条不变量写在这里，不指望调用方自觉：</p>
 * <ul>
 *   <li>状态只能经 {@link #transitionTo(ProposalState)} 变更，合法性由状态机裁决</li>
 *   <li>同一目标同时只允许一个进行中提案：靠 {@code open_target_key} 的唯一键兜底
 *       （{@code OPEN}/{@code ESCALATED} 期间占用，结案后置 null 释放），不依赖先查后插</li>
 * </ul>
 *
 * <p>不提供 setter。票数不落在这里——由 {@link ReviewVote} 实时聚合（方案 6.3：委员会 ≤ 6 人，
 * 没有任何性能理由去维护一个可能与票不同步的冗余计数）。</p>
 */
@Getter
@Entity
@Table(name = "review_proposal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewProposal extends BaseEntity {

    /** open_target_key 的长度：POST/COMMENT + ':' + 自增 id，40 足够 */
    private static final int TARGET_KEY_LENGTH = 40;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private ProposalAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 32)
    private ReportReason reasonCode;

    @Column(name = "detail", length = 500)
    private String detail;

    /** 发起人。记下来是为了：① 他不能再复审自己推动的处置（复审独立性） ② 界面显示「谁发起的」给超管 */
    @Column(name = "proposer_id", nullable = false)
    private Long proposerId;

    /** 表决分母快照（提案创建时的在册委员会人数），全程不变 */
    @Column(name = "snapshot_size", nullable = false)
    private int snapshotSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProposalStatus status;

    /** 结论说明：表决通过（提案 #12，4/6 同意）/ 超管裁决 / 执行失败原因 */
    @Column(name = "resolution", length = 200)
    private String resolution;

    /**
     * 裁决人：**仅超管裁决时**写入其 userId；表决通过、僵局上报、处置无法执行等由委员会或系统
     * 走完的路径一律为 null。
     *
     * <p>于是「是否由超管拍板」= {@code resolvedBy != null}，这正是设计 6.1 想用
     * {@code DECIDED_BY_SUPER} 状态表达的那件事——把它做成字段而不是状态，是因为「结论」与
     * 「路径」是两个维度，混进状态会让每个「是否通过」的查询都要写成 {@code status IN (...)}。</p>
     *
     * <p>刻意**不**在这里写发起人：发起人已有 {@link #proposerId}，重复记录会让上面那个判定失真
     * （超管本人也可能是发起人）。</p>
     */
    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** 进行中提案的目标键；结案后置 null 以释放目标（MySQL 没有部分唯一索引，故用可空列模拟） */
    @Column(name = "open_target_key", length = TARGET_KEY_LENGTH)
    private String openTargetKey;

    /** 建提案：目标类型由动作推导，避免「动作与目标类型不匹配」这种组合被写进库 */
    public static ReviewProposal open(Long targetId, ProposalAction action, Long proposerId,
                                      ReportReason reasonCode, String detail, int snapshotSize) {
        ReviewProposal proposal = new ReviewProposal();
        proposal.targetType = action.targetType();
        proposal.targetId = targetId;
        proposal.action = action;
        proposal.proposerId = proposerId;
        proposal.reasonCode = reasonCode;
        proposal.detail = detail;
        proposal.snapshotSize = snapshotSize;
        proposal.status = new ProposalState.Open().status();
        proposal.openTargetKey = targetKey(action.targetType(), targetId);
        return proposal;
    }

    /** 状态迁移唯一入口，非法迁移直接抛错（禁止跳态、禁止终态再变） */
    public void transitionTo(ProposalState next) {
        ProposalState.assertTransition(this.status, next.status());
        this.status = next.status();
        switch (next) {
            case ProposalState.Approved approved -> {
                this.resolvedBy = approved.decidedBy();
                this.resolution = approved.resolution();
                this.resolvedAt = Instant.now();
                // 结案即释放目标：同一内容之后可以再被提案（例如恢复后又被举报）
                this.openTargetKey = null;
            }
            case ProposalState.Rejected rejected -> {
                this.resolvedBy = rejected.decidedBy();
                this.resolution = rejected.resolution();
                this.resolvedAt = Instant.now();
                this.openTargetKey = null;
            }
            // Open / Escalated 都不结案：ESCalated 仍占用目标（否则僵局期间又能再提一条，等于绕过）
            case ProposalState.Open ignored -> {
            }
            case ProposalState.Escalated ignored -> {
            }
        }
    }

    /** 是否进行中（可投票 / 可结案） */
    public boolean isOpen() {
        return status == ProposalStatus.OPEN;
    }

    /** 是否等待超管裁决 */
    public boolean isEscalated() {
        return status == ProposalStatus.ESCALATED;
    }

    public static String targetKey(ReviewTargetType targetType, Long targetId) {
        return targetType.name() + ":" + targetId;
    }
}
