package com.qinglan.review.entity;

/**
 * 审核体系的目标类型。
 *
 * <p>举报与申诉共用：举报可指向帖子或评论，申诉指向被处置的同一批目标。</p>
 */
public enum ReviewTargetType {
    POST,
    COMMENT
}
