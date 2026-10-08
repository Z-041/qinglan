package com.qinglan.review.repository;

import com.qinglan.review.entity.ReviewVote;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 表决票仓储。
 *
 * <p>与 {@code AuditLogRepository} 同性质：刻意继承最小化的 {@link Repository} 而非
 * {@code JpaRepository}，编译期就不存在 update / delete 入口——票只增不改删（方案 6.2），
 * 投出去的票不能改，要改只能结案后重新提案。只声明本模块真正用到的方法。</p>
 */
public interface ReviewVoteRepository extends Repository<ReviewVote, Long> {

    ReviewVote save(ReviewVote vote);

    /** 单个提案的全部票：票数 ≤ 委员会上限，无需分页 */
    List<ReviewVote> findByProposalId(Long proposalId);

    /** 一页提案的全部票，批量装配避免逐行查询（N+1） */
    List<ReviewVote> findByProposalIdIn(Collection<Long> proposalIds);

    /** 判断投票人是否已就该提案投过票：业务层据此把重复投票映射成 409，唯一键仍是最终防线 */
    Optional<ReviewVote> findByProposalIdAndVoterId(Long proposalId, Long voterId);
}
