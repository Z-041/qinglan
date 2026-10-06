package com.qinglan.review.entity;

/**
 * 违规类型。
 *
 * <p>举报理由与管理员处置（下架/移除）的违规类型共用同一套枚举，避免「用户举报说 A、
 * 管理员下架记成 B」造成对不上；审核队列与审计日志因此可以共用口径。</p>
 */
public enum ReportReason {
    /** 广告与推广 */
    ADVERTISEMENT,
    /** 色情低俗 */
    PORNOGRAPHY,
    /** 辱骂、人身攻击 */
    ABUSE,
    /** 泄露他人隐私 */
    PRIVACY,
    /** 诈骗与欺诈 */
    FRAUD,
    /** 违法违规 */
    ILLEGAL,
    /** 与话题无关 */
    IRRELEVANT,
    /** 其他，需在 detail 里说明 */
    OTHER
}
