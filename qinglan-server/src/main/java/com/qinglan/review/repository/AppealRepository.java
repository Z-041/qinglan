package com.qinglan.review.repository;

import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ReviewTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    /**
     * 每个目标只受理一次申诉：业务层用它把重复申诉映射成 409。
     * 并发下仍以 {@code uk_appeal_target} 为最终防线。
     */
    Optional<Appeal> findByTargetTypeAndTargetId(ReviewTargetType targetType, Long targetId);

    long countByStatus(AppealStatus status);

    /**
     * 待复审申诉的键集分页查询。
     *
     * <p>排序键 {@code (created_at asc, id asc)}——申诉先到先审（FIFO）。与举报队列的
     * 「举报数降序 + 首报时间升序」不同：申诉没有可聚合的权重，等得越久越该先看。
     * 游标是这两列的复合值，两个比较条件必须与 {@code order by} 严格同序。</p>
     *
     * <p>只查 {@code PENDING}：已复审的申诉不再出现在工作台。</p>
     */
    @Query("""
            select a from Appeal a
            where a.status = :status
              and (:cursorCreatedAt is null
                   or a.createdAt > :cursorCreatedAt
                   or (a.createdAt = :cursorCreatedAt and a.id > :cursorId))
            order by a.createdAt asc, a.id asc
            """)
    List<Appeal> findAdminPage(
            @Param("status") AppealStatus status,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable limit);
}
