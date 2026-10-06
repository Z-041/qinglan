package com.qinglan.review.repository;

import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.ReviewTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    /**
     * 每个目标只受理一次申诉：业务层用它把重复申诉映射成 409。
     * 并发下仍以 {@code uk_appeal_target} 为最终防线。
     */
    Optional<Appeal> findByTargetTypeAndTargetId(ReviewTargetType targetType, Long targetId);
}
