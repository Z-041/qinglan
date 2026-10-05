package com.qinglan.post.entity;

/**
 * 联系方式可见性（设计方案 4.3）。
 *
 * <p>另外两种情形不由本字段表达，而在返回 DTO 时按 viewer 身份判定：
 * 未认证 viewer 一律拿不到联系方式（不变量 #2）。</p>
 */
public enum ContactVisibility {
    /** 认证用户可见 */
    VISIBLE,
    /** 被管理员处罚为仅作者可见 */
    HIDDEN
}
