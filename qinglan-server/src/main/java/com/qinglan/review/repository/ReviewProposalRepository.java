package com.qinglan.review.repository;

import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewTargetType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 处置提案仓储（P5 二期）。
 *
 * <p>提案是可变实体（状态迁移、结案人、结论），故继承 {@link JpaRepository}；与审计日志不同，
 * 这里没有「只增不改删」的约束——但状态只能经状态机变更，仓储不提供任何直接改状态的入口。</p>
 */
public interface ReviewProposalRepository extends JpaRepository<ReviewProposal, Long> {

    /**
     * 按主键加排他行锁读取（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>投票与裁决都先用它拿到行锁再判定：同一提案的最后一票可能被两个请求同时投出，
     * 没有锁的话两边都会算出「达到阈值」并各自执行一次处置。锁在同一事务内持有，
     * 结案（含执行处置时的批量 UPDATE 清一级缓存）都不影响它。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ReviewProposal p where p.id = :id")
    Optional<ReviewProposal> findForUpdate(@Param("id") Long id);

    /**
     * 提案列表：键集分页 + 可选状态 / 发起人过滤，新提案在前。
     *
     * <p>排序键 {@code (created_at desc, id desc)}，比较条件必须与 {@code order by} 严格同序，
     * 否则翻页会漏行或重复。首页传 {@code cursorCreatedAt = null}（第一个分支恒真），
     * 与后续页共用同一条 JPQL。</p>
     *
     * <p>{@code proposerId} 用于「我的提案」视图；为 null 时不过滤（委员会看全部）。
     * 列表不返回发起人身份（{@link com.qinglan.review.dto.ProposalSummaryResponse} 约束），
     * 过滤只在服务端进行。</p>
     */
    @Query("""
            select p from ReviewProposal p
            where (:status is null or p.status = :status)
              and (:proposerId is null or p.proposerId = :proposerId)
              and (:cursorCreatedAt is null
                   or p.createdAt < :cursorCreatedAt
                   or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<ReviewProposal> findAdminPage(
            @Param("status") ProposalStatus status,
            @Param("proposerId") Long proposerId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);

    /**
     * 一批目标上「占用目标」的提案：进行中（OPEN）与僵局待裁决（ESCALATED）。
     *
     * <p>供审核队列行内显示提案进度（方案 9.2）——同目标最多一条（{@code uk_proposal_open_target}
     * 保证），因此调用方可以直接按目标建映射。批量取而不是逐行查：队列一页 20 行，逐行就是
     * 20 次往返。</p>
     */
    @Query("""
            select p from ReviewProposal p
            where p.targetType = :targetType
              and p.targetId in :targetIds
              and p.status in (com.qinglan.review.entity.ProposalStatus.OPEN,
                               com.qinglan.review.entity.ProposalStatus.ESCALATED)
            """)
    List<ReviewProposal> findOpenByTargets(
            @Param("targetType") ReviewTargetType targetType,
            @Param("targetIds") Collection<Long> targetIds);
}
