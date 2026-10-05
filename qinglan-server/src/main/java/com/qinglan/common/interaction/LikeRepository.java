package com.qinglan.common.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<LikeRecord, Long> {

    Optional<LikeRecord> findByTargetTypeAndTargetIdAndUserId(LikeTargetType targetType, Long targetId, Long userId);

    /** 批量取点赞数，供列表一次查完，避免逐条统计造成 N+1 */
    @Query("""
            select new com.qinglan.common.interaction.LikeCount(l.targetId, count(l))
            from LikeRecord l
            where l.targetType = :targetType and l.targetId in :targetIds
            group by l.targetId
            """)
    List<LikeCount> countByTargets(
            @Param("targetType") LikeTargetType targetType,
            @Param("targetIds") Collection<Long> targetIds
    );

    /** 批量判断当前用户点过哪些，同样是为了避免 N+1 */
    @Query("""
            select l.targetId from LikeRecord l
            where l.targetType = :targetType and l.userId = :userId and l.targetId in :targetIds
            """)
    List<Long> findLikedTargetIds(
            @Param("targetType") LikeTargetType targetType,
            @Param("userId") Long userId,
            @Param("targetIds") Collection<Long> targetIds
    );

    long countByTargetTypeAndTargetId(LikeTargetType targetType, Long targetId);

    /**
     * 目标被永久删除时，连同其点赞行一并清掉，避免留下再也读不到、也不会被回收的孤儿数据（R-15）。
     *
     * <p>只允许在**不可撤回**的删除路径上调用：评论删除是软删且没有恢复入口，可以清；
     * 帖子下架可申诉恢复（不变量 #5 的 {@code TakenDown → Approved}），所以帖子的点赞行不能这么删。</p>
     */
    @Modifying
    @Query("delete from LikeRecord l where l.targetType = :targetType and l.targetId in :targetIds")
    int deleteByTargets(@Param("targetType") LikeTargetType targetType, @Param("targetIds") Collection<Long> targetIds);
}
