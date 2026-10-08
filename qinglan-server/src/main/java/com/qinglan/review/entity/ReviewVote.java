package com.qinglan.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 一张表决票。
 *
 * <p>与审计日志同性质：**只增不改删**，所以和 {@code AuditLog} 一样不继承 {@code BaseEntity}
 * （没有 updated_at，也永远不会有），仓储不继承 {@code JpaRepository}。票不可改也意味着
 * 「先投同意、事后改不同意」这条路不存在；要改只能由提案结案后重新提案。</p>
 *
 * <p>一人一票由 {@code uk_vote_proposal_voter} 兜底：并发下重复插入会被数据库拒绝，
 * 服务层据此映射成 409（不静默吞掉）。</p>
 */
@Getter
@Entity
@Table(name = "review_vote")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_id", nullable = false)
    private Long proposalId;

    @Column(name = "voter_id", nullable = false)
    private Long voterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 10)
    private VoteDecision decision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ReviewVote of(Long proposalId, Long voterId, VoteDecision decision) {
        ReviewVote vote = new ReviewVote();
        vote.proposalId = proposalId;
        vote.voterId = voterId;
        vote.decision = decision;
        vote.createdAt = Instant.now();
        return vote;
    }
}
