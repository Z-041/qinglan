package com.qinglan.common.interaction;

/**
 * 点赞数投影，供 {@link LikeRepository#countByTargets} 的构造器表达式使用。
 * 字段用包装类型：JPQL 的 {@code count()} 返回 Long，声明成原始类型会导致构造器不匹配。
 */
public record LikeCount(Long targetId, Long count) {
}
