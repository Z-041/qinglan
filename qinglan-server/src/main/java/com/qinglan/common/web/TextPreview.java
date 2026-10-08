package com.qinglan.common.web;

/**
 * 文本摘要：按**码点**截断并补省略号。
 *
 * <p>为什么不能直接 {@code substring(0, n)}：{@code String} 的索引是 UTF-16 码元，而 emoji 等
 * 增补平面字符占两个码元（代理对）。按码元切会把代理对切成半个，前端渲染成乱码方框——通知正文、
 * 队列摘要这些「给用户 / 管理员看的一行字」都会中招。</p>
 *
 * <p>放在 {@code common} 而不是某个业务模块：它被 review 的四个队列摘要与 comment 的通知正文共用，
 * 放进任何一个业务模块都会让另一个模块产生不该有的依赖边（AGENTS.md 第三章）。放在
 * {@code common/web} 是因为它的唯一用途是装配**对外展示**的文本，与 {@link CursorCodec} 同类。</p>
 *
 * <p>纯函数，无状态。</p>
 */
public final class TextPreview {

    private static final String ELLIPSIS = "...";

    private TextPreview() {
    }

    /**
     * 超过 {@code maxCodePoints} 个码点时截断并补 {@code ...}；不超过则原样返回。
     *
     * @param text         待摘要文本；为 {@code null} 时返回 {@code null}（调用方的「目标已删除」
     *                     等场景依赖这个语义，前端按 null 处理）
     * @param maxCodePoints 保留的码点数，不得为负
     */
    public static String of(String text, int maxCodePoints) {
        if (text == null) {
            return null;
        }
        if (maxCodePoints < 0) {
            throw new IllegalArgumentException("摘要长度不能为负：" + maxCodePoints);
        }
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        // offsetByCodePoints 会跳过完整的代理对，保证不会切出半个字符
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints)) + ELLIPSIS;
    }
}
