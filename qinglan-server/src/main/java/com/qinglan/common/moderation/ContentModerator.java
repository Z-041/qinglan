package com.qinglan.common.moderation;

/**
 * 内容审核端口（预留接入点）。
 *
 * <p>P1 不含真实的内容安全能力，默认实现 {@link AllowAllContentModerator} 一律放行。
 * 后续对接独立的内容安全服务（可由 Python 生态实现，以 HTTP / gRPC 暴露）时，
 * 只需替换本接口的实现 Bean，发布与评论链路无需改动。</p>
 */
public interface ContentModerator {

    /** 审核一段用户文本 */
    ModerationResult moderate(String content);
}
