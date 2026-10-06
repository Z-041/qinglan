package com.qinglan.common.moderation;

import org.springframework.stereotype.Component;

/**
 * 端口默认实现：一律放行。
 *
 * <p>P1 把调用点先固定下来，真实拦截待接入内容安全服务后由新的实现替换。</p>
 */
@Component
public class AllowAllContentModerator implements ContentModerator {

    @Override
    public ModerationResult moderate(String content) {
        return ModerationResult.pass();
    }
}
