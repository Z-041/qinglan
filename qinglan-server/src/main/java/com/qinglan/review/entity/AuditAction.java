package com.qinglan.review.entity;

/**
 * 需要留痕的管理动作。仅登记当前阶段真实使用的动作，后续阶段按需扩展。
 */
public enum AuditAction {
    BAN,
    UNBAN,
    CREDIT_ADJUST
}
