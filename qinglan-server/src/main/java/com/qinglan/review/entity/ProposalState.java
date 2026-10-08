package com.qinglan.review.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;

import java.util.Map;
import java.util.Set;

/**
 * 提案状态机（P5 方案 5.7，与 {@link com.qinglan.post.entity.ReviewState} 同风格）。
 *
 * <p>本接口是提案状态迁移合法性的**唯一裁决者**，实体不得绕过它直接改 {@link ProposalStatus}。允许的迁移：</p>
 *
 * <pre>
 * Open → Approved（表决达到阈值，或超管裁决准予）
 * Open → Escalated（同意票已不可能过半）
 * Open → Rejected（结案前目标状态已变，处置无法执行）
 * Escalated → Approved / Rejected（超管裁决）
 * </pre>
 *
 * <p>终态不再迁移：已结案的提案不能被重新投票或再次裁决——「先投票后被超管改判」这条路必须堵死，
 * 否则决议可以被反复推翻。</p>
 */
public sealed interface ProposalState
        permits ProposalState.Open,
                ProposalState.Escalated,
                ProposalState.Approved,
                ProposalState.Rejected {

    /** 该状态落库时写入的取值 */
    ProposalStatus status();

    /** 合法迁移表：key 为当前状态，value 为允许到达的状态 */
    Map<ProposalStatus, Set<ProposalStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProposalStatus.OPEN, Set.of(ProposalStatus.APPROVED, ProposalStatus.REJECTED,
                    ProposalStatus.ESCALATED),
            ProposalStatus.ESCALATED, Set.of(ProposalStatus.APPROVED, ProposalStatus.REJECTED),
            ProposalStatus.APPROVED, Set.of(),
            ProposalStatus.REJECTED, Set.of());

    /** 进行中：可投票 */
    record Open() implements ProposalState {
        @Override
        public ProposalStatus status() {
            return ProposalStatus.OPEN;
        }
    }

    /** 僵局：同意票已不可能过半，等超管裁决。目标仍被占用，不接受新提案与投票 */
    record Escalated() implements ProposalState {
        @Override
        public ProposalStatus status() {
            return ProposalStatus.ESCALATED;
        }
    }

    /**
     * 准予处置。
     *
     * @param decidedBy  裁决人：超管裁决时为其 userId；表决通过时为 {@code null}
     *                   ——委员会的共同结论没有单人裁决者，发起人另存于 {@code proposer_id}。
     *                   于是「是否超管拍板」= {@code decidedBy != null}（方案 6.1 用它取代了
     *                   {@code DECIDED_BY_SUPER} 状态）
     * @param resolution 结论说明，写进提案表并对委员会可见（「表决通过（提案 #12，4/6 同意）」等）
     */
    record Approved(Long decidedBy, String resolution) implements ProposalState {
        @Override
        public ProposalStatus status() {
            return ProposalStatus.APPROVED;
        }
    }

    /** 不予处置：超管裁决驳回，或结案前目标状态已变导致处置无法执行 */
    record Rejected(Long decidedBy, String resolution) implements ProposalState {
        @Override
        public ProposalStatus status() {
            return ProposalStatus.REJECTED;
        }
    }

    /** 校验迁移是否合法，非法即抛 ILLEGAL_REVIEW_TRANSITION（禁止跳态、禁止终态再变） */
    static void assertTransition(ProposalStatus from, ProposalStatus to) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
    }

    static boolean canTransition(ProposalStatus from, ProposalStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
