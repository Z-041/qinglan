package com.qinglan.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文本摘要的单元测试。
 *
 * <p>核心是「不切断代理对」：{@code substring(0, n)} 按 UTF-16 码元切，emoji 会被切成半个，
 * 在通知正文与队列摘要里渲染成乱码。这类断言只有用增补平面字符才测得出来——纯 ASCII 用例
 * 对新旧实现都是绿的，等于没测。</p>
 */
class TextPreviewTest {

    @Test
    @DisplayName("未超长时原样返回，不加省略号")
    void shouldKeepTextWithinLimit() {
        assertThat(TextPreview.of("短文本", 10)).isEqualTo("短文本");
    }

    @Test
    @DisplayName("恰好等于上限时也不加省略号")
    void shouldNotAddEllipsisAtExactLimit() {
        assertThat(TextPreview.of("12345", 5)).isEqualTo("12345");
    }

    @Test
    @DisplayName("超长时截断并补省略号")
    void shouldTruncateLongText() {
        assertThat(TextPreview.of("1".repeat(200), 80)).isEqualTo("1".repeat(80) + "...");
    }

    @Test
    @DisplayName("按码点截断：不会把 emoji 的代理对切成半个（否则渲染成乱码）")
    void shouldNotSplitSurrogatePair() {
        String emojis = "😀😀😀";

        // 3 个 emoji 只占 3 个码点、却有 6 个 char：按 char 切会切出半个代理对
        assertThat(TextPreview.of(emojis, 2)).isEqualTo("😀😀...");
        assertThat(TextPreview.of(emojis, 2).codePointCount(0, TextPreview.of(emojis, 2).length()))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("emoji 与中文混排时按码点计数")
    void shouldCountCodePointsForMixedText() {
        // 5 个码点：😀 算 1、中 与 文 各算 1
        assertThat(TextPreview.of("😀中文😀", 3)).isEqualTo("😀中文...");
    }

    @Test
    @DisplayName("null 原样返回 null：调用方靠它区分「目标已删除」")
    void shouldReturnNullForNullInput() {
        assertThat(TextPreview.of(null, 10)).isNull();
    }

    @Test
    @DisplayName("负数上限是调用方的编程错误，直接抛错而不是静默返回空串")
    void shouldRejectNegativeLimit() {
        assertThatThrownBy(() -> TextPreview.of("文本", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
