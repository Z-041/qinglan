package com.qinglan.common.moderation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 本地硬拦截配置。
 *
 * @param sensitiveWordFile 敏感词库的 classpath 位置，一行一词，{@code #} 开头为注释
 * @param privacyRules      隐私信息正则；命中即拒绝发布。默认只含身份证与银行卡——
 *                          学号正则刻意留空：{@code \d{9,12}} 会把 11 位手机号一起误拦，
 *                          等确认真实学号格式后再以配置追加，不改代码
 */
@ConfigurationProperties(prefix = "qinglan.moderation")
public record ModerationProperties(String sensitiveWordFile, List<PrivacyRule> privacyRules) {

    public ModerationProperties {
        privacyRules = privacyRules == null ? List.of() : List.copyOf(privacyRules);
    }

    /**
     * @param label 仅用于日志，便于定位是哪条规则误伤；不打印命中的原文（那是 PII）
     * @param regex 正则表达式，命中用 {@code find()} 判定
     */
    public record PrivacyRule(String label, String regex) {
    }
}
