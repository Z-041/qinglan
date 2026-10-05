package com.qinglan.common.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
