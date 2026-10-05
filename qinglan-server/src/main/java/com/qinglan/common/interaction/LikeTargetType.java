package com.qinglan.common.interaction;

/**
 * 点赞目标类型。点赞是 post 与 comment 共用的互动能力，
 * 故实体与仓储放在下级公共层，避免 post 与 comment 之间产生横向依赖。
 */
public enum LikeTargetType {
    POST,
    COMMENT
}
