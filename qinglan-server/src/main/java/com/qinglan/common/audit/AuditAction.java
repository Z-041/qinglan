package com.qinglan.common.audit;

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
    CONTACT_VIEW,
    /** 举报数达阈值，系统自动下架待人工复核（operator 为 {@code SYSTEM_OPERATOR_ID}） */
    POST_AUTO_TAKEDOWN,
    /** 管理员下架（Approved → TakenDown） */
    POST_TAKEDOWN,
    /** 恢复可见（TakenDown → Approved），申诉通过也落这一条 */
    POST_RESTORE,
    /** 隐藏联系方式，改为仅作者本人可见（不变量 #2 的处置侧） */
    POST_HIDE_CONTACT,
    /** 撤销隐藏联系方式，恢复认证用户可见（误判的纠正路径） */
    POST_SHOW_CONTACT,
    /** 管理员移除评论——评论先发后审，这是唯一的兜底处置手段 */
    COMMENT_REMOVE,
    /** 驳回举报：内容不违规，待处理举报结案、举报数清零 */
    REPORT_DISMISS,
    /** 维持下架：确认举报成立，待处理举报结案、举报数清零（内容已在下架态，无状态迁移） */
    REPORT_UPHOLD,
    /** 复审申诉（通过时另有 POST_RESTORE 记录内容状态变更，本条记录审核决定本身） */
    APPEAL_REVIEW
}
