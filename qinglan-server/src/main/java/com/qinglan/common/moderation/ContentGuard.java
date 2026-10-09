package com.qinglan.common.moderation;

import com.qinglan.common.error.BizException;

/**
 * 本地硬拦截：入库前同步拦下「确定违规」的内容（P2 只做这一层）。
 *
 * <p>无外部依赖的确定性校验（敏感词 + 隐私格式），命中即拒、同步返回；词库与正则在启动时
 * 一次性加载，规则见 {@link LocalContentGuard}。</p>
 *
 * <p>命中即抛 {@link BizException}：敏感词用 {@code CONTENT_REJECTED}，隐私格式用
 * {@code PRIVACY_CONTENT_BLOCKED}。调用方不需要再判断返回值，避免各调用点各写一遍分支。</p>
 */
@FunctionalInterface
public interface ContentGuard {

    /**
     * @param text 用户提交的文本；{@code null} 或空白视为放行
     * @throws BizException 命中敏感词或隐私格式
     */
    void check(String text);
}
