package com.qinglan.common.security;

/**
 * 管理端权限点（P5 权限模型的唯一来源）。
 *
 * <p>为什么要权限点而不是直接判角色：角色回答的是「能进后台吗」，权限点回答的是「进去以后能做什么」。
 * 直接用角色判，每加一个页面就要在每个判点重复一遍「ADMIN 或 SUPER_ADMIN」，两端口径必然漂移
 * （R-28 记过「角色准入三处各写一份」的教训）。收敛成权限点后：<b>角色 → 权限点</b>的映射只有
 * {@code UserRole#permissions()} 一处，接口只写 {@code hasAuthority('X')}，前端只按后端下发的
 * 权限点渲染——加一档角色不必动前端与既有端点。</p>
 *
 * <p>放在 {@code common} 而非某个业务模块：它是跨模块的授权词汇表，且 common 不依赖任何业务模块，
 * 因而任何模块都能引用；把它放进 user/review 都会让别的模块被迫去依赖那个模块。</p>
 *
 * <p>注意：前端隐藏只是体验层，真正的防线是端点上的 {@code @PreAuthorize}——
 * 与「联系方式脱敏在服务端完成」同一条原则（AGENTS.md 不变量 #2）。</p>
 */
public enum AdminPermission {

    /** 治理数据的读：待审帖子列表、审核队列、审核上下文（不含用户管理与审计） */
    MODERATION_VIEW,
    /** 待审帖子通过 / 驳回（单人动作，不需要表决） */
    POST_REVIEW,
    /** 驳回举报：内容不违规、举报结案（单人动作，不需要表决） */
    REPORT_DISMISS,
    /** 申诉复审：含申诉队列的读与 accept / reject */
    APPEAL_REVIEW,
    /** 对**已发布内容**发起处置提案（下架 / 移除评论 / 隐藏联系方式 / 维持下架） */
    PROPOSAL_CREATE,
    /** 对提案投票（隐含可查看提案与汇总进度） */
    PROPOSAL_VOTE,
    /** 用户管理：列表与筛选的读 + 封禁 / 解封 / 调信用分 */
    USER_MANAGE,
    /** 公告管理页与建草稿 */
    ANNOUNCEMENT_DRAFT,
    /** 发布 / 撤回公告（高侵入能力，仅超级管理员） */
    ANNOUNCEMENT_MANAGE,
    /** 数据看板 */
    STATS_VIEW,
    /** 角色下放 / 收回（含评议员申请审批） */
    ROLE_GRANT,
    /** 表决僵局的裁决（仅超级管理员） */
    PROPOSAL_ARBITRATE,
    /** 审计日志查询（仅超级管理员） */
    AUDIT_READ
}
