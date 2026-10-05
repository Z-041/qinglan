package com.qinglan.post.entity;

/**
 * 机审风险等级。P1 仅作为状态机 {@link ReviewState.Pending} 的载荷占位，
 * P2 接入内容安全 API 后由机审写入。
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
