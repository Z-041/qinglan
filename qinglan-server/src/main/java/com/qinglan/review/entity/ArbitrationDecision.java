package com.qinglan.review.entity;

/**
 * 超管裁决选项（P5 方案 5.4）。
 *
 * <p>只用于僵局（{@link ProposalStatus#ESCALATED}）的提案：委员会无法达成多数时，由超级管理员
 * 单人决定。与投票的 {@link VoteDecision} 分开，是因为两者语义不同——投票是「我同意/不同意这次处置」，
 * 裁决是「准予执行/不予执行」，混用会让 {@code AGREE} 在裁决语境下读起来像「超管投了同意票」。</p>
 */
public enum ArbitrationDecision {
    /** 准予处置：执行处置，提案结案为 APPROVED（路径不同于表决通过，但结论一致） */
    GRANT,
    /** 不予处置：提案结案为 REJECTED，内容保持原状 */
    DENY
}
