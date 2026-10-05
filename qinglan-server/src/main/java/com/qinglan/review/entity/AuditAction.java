package com.qinglan.review.entity;

/**
 * 需要留痕的管理动作。仅登记当前阶段真实使用的动作，后续阶段按需扩展。
 */
public enum AuditAction {
    BAN,
    UNBAN,
    CREDIT_ADJUST,
    /** 放行帖子（Pending/Reviewing → Approved） */
    POST_APPROVE,
    /** 驳回帖子（Reviewing → Rejected） */
    POST_REJECT,
    /** 查看帖子联系方式，用于追溯恶意批量抓取（不变量 #2） */
    CONTACT_VIEW
}
