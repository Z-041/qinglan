package com.qinglan.review.entity;

/**
 * 申诉的处理状态。
 */
public enum AppealStatus {
    /** 待复审 */
    PENDING,
    /** 复审通过：内容恢复 */
    ACCEPTED,
    /** 复审维持原判 */
    REJECTED
}
