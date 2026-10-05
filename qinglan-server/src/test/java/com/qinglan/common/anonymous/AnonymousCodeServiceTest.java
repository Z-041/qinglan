package com.qinglan.common.anonymous;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不变量 #1：匿名代号跨帖不可关联。
 */
class AnonymousCodeServiceTest {

    private static final String SALT = "9f2c4a1b7d3e50618c9a2b4d6f8e0a1c";
    private static final long AUTHOR_ID = 1001L;

    private final AnonymousCodeService anonymousCodeService = new AnonymousCodeService();

    @Test
    @DisplayName("同一用户在同一个帖子下代号稳定可复现")
    void shouldBeDeterministicForSamePostAndAuthor() {
        String first = anonymousCodeService.deriveCode(10L, AUTHOR_ID, SALT);
        String second = anonymousCodeService.deriveCode(10L, AUTHOR_ID, SALT);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("同一用户在不同帖子下代号不同，无法跨帖关联")
    void shouldDifferAcrossPostsForSameAuthor() {
        String onPostTen = anonymousCodeService.deriveCode(10L, AUTHOR_ID, SALT);
        String onPostEleven = anonymousCodeService.deriveCode(11L, AUTHOR_ID, SALT);
        String onPostTwelve = anonymousCodeService.deriveCode(12L, AUTHOR_ID, SALT);

        assertThat(onPostTen).isNotEqualTo(onPostEleven);
        assertThat(onPostEleven).isNotEqualTo(onPostTwelve);
        assertThat(onPostTen).isNotEqualTo(onPostTwelve);
    }

    @Test
    @DisplayName("不同用户在同一帖子下代号不同")
    void shouldDifferAcrossAuthors() {
        String authorOne = anonymousCodeService.deriveCode(10L, 1001L, SALT);
        String authorTwo = anonymousCodeService.deriveCode(10L, 1002L, SALT);

        assertThat(authorOne).isNotEqualTo(authorTwo);
    }

    @Test
    @DisplayName("代号格式为「词#四位十六进制」且不包含作者 ID")
    void shouldMatchExpectedFormatAndNotExposeAuthorId() {
        String code = anonymousCodeService.deriveCode(10L, AUTHOR_ID, SALT);

        assertThat(code).matches("[\\p{IsHan}]{2}#[0-9a-f]{4}");
        assertThat(code).doesNotContain(String.valueOf(AUTHOR_ID));
    }
}
