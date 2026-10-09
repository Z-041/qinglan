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
    APPEAL_REVIEW,
    /** 下放管理权限：把某用户提升为管理员 / 学生评议员（P5） */
    ROLE_GRANT,
    /** 收回管理权限：把某用户的角色降回学生，或委员会内部降档（P5） */
    ROLE_REVOKE,
    /**
     * 发起处置提案（P5 二期）。
     *
     * <p>提案表本身存了发起人，但委员会界面刻意不展示「谁发起的」（与「投票只可见汇总」同一取向）；
     * 这条审计是留给超级管理员追溯用的——他要能看到全部治理轨迹。</p>
     */
    PROPOSAL_CREATE,
    /** 提案结案：表决通过并执行处置 / 上报超管 / 超管裁决 / 处置无法执行（P5 二期） */
    PROPOSAL_RESOLVE,
    /** 创建公告草稿（仅 SUPER_ADMIN 可发布，普通管理员只能建草稿） */
    ANNOUNCEMENT_CREATE,
    /** 发布公告（仅 SUPER_ADMIN），发布后进入强制阅读 */
    ANNOUNCEMENT_PUBLISH,
    /** 撤回公告（仅 SUPER_ADMIN），不删已读记录，更正走 supersedes_id 发新公告 */
    ANNOUNCEMENT_REVOKE,
    /**
     * 批量导出（审计日志 / 用户列表）。
     *
     * <p>导出是「一次性把大量数据带离系统」的敏感动作，属不变量 #7 的意图范围：留痕才能回答
     * 「谁在什么时候把哪批数据导走了」。{@code reason} 记录导出资源与筛选条件摘要，
     * 不含任何身份明文（不变量 #3）。</p>
     */
    DATA_EXPORT
}
