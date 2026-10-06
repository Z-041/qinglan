package com.qinglan.common.moderation;

import com.qinglan.common.error.BizException;

/**
 * 本地硬拦截：入库前同步拦下「确定违规」的内容（P2 只做这一层）。
 *
 * <p>与预留端口 {@link ContentModerator} 的分工：</p>
 * <ul>
 *   <li>{@code ContentGuard} 是无外部依赖的确定性校验（敏感词 + 隐私格式），命中即拒、同步返回</li>
 *   <li>{@link ContentModerator} 是将来对接外部内容安全服务（可由 Python 生态实现）的端口，
 *       当前未接线，默认实现一律放行</li>
 * </ul>
 *
 * <p>命中即抛 {@link BizException}：敏感词用 {@code CONTENT_REJECTED}，隐私格式用
 * {@code PRIVACY_CONTENT_BLOCKED}。调用方不需要再判断返回值，避免三处调用各写一遍分支。</p>
 */
@FunctionalInterface
public interface ContentGuard {

    /**
     * @param text 用户提交的文本；{@code null} 或空白视为放行
     * @throws BizException 命中敏感词或隐私格式
     */
    void check(String text);
}
