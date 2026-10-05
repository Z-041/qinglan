package com.qinglan.comment.entity;

/**
 * 评论状态。评论走「先发后审」（设计方案第一章红线 2），
 * 因此不进主帖那套审核状态机，只有「正常 / 已移除」两态。
 */
public enum CommentStatus {
    PUBLISHED,
    REMOVED
}
