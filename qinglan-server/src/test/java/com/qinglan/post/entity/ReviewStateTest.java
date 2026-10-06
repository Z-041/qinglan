package com.qinglan.post.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不变量 #5：审核状态迁移必须合法，禁止跳态。
 *
 * <p>这是七条不变量里唯一可以纯逻辑验证的一条，因此把边界钉死在这里——
 * 后续任何新增状态都必须同步改 {@link ReviewState#permits} 并补这里的用例。</p>
 */
class ReviewStateTest {

    @Test
    @DisplayName("不变量 #5：设计文档列出的迁移路径全部允许")
    void shouldAllowDocumentedTransitions() {
        assertThat(ReviewState.canTransition(PostStatus.DRAFT, PostStatus.PENDING)).isTrue();
        assertThat(ReviewState.canTransition(PostStatus.PENDING, PostStatus.REVIEWING)).isTrue();
        assertThat(ReviewState.canTransition(PostStatus.REVIEWING, PostStatus.APPROVED)).isTrue();
        assertThat(ReviewState.canTransition(PostStatus.REVIEWING, PostStatus.REJECTED)).isTrue();
        assertThat(ReviewState.canTransition(PostStatus.APPROVED, PostStatus.TAKEN_DOWN)).isTrue();
        // 申诉通过：下架回到已通过
        assertThat(ReviewState.canTransition(PostStatus.TAKEN_DOWN, PostStatus.APPROVED)).isTrue();
    }

    @Test
    @DisplayName("不变量 #5：跳态与非法回退一律禁止")
    void shouldRejectIllegalTransitions() {
        assertThat(ReviewState.canTransition(PostStatus.PENDING, PostStatus.APPROVED)).isFalse();
        assertThat(ReviewState.canTransition(PostStatus.DRAFT, PostStatus.APPROVED)).isFalse();
        assertThat(ReviewState.canTransition(PostStatus.TAKEN_DOWN, PostStatus.PENDING)).isFalse();
        assertThat(ReviewState.canTransition(PostStatus.REJECTED, PostStatus.APPROVED)).isFalse();
        assertThat(ReviewState.canTransition(PostStatus.APPROVED, PostStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("不变量 #5：非法迁移抛 ILLEGAL_REVIEW_TRANSITION")
    void shouldThrowOnIllegalTransition() {
        assertThatThrownBy(() -> ReviewState.assertTransition(PostStatus.PENDING, PostStatus.APPROVED))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
    }

    @Test
    @DisplayName("不变量 #5：跳态由实体自身拦截，不依赖调用方自觉")
    void shouldGuardTransitionInsideEntity() {
        Post post = Post.draft(1L, 1L, "正文", null);
        post.submit(RiskLevel.LOW);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);

        assertThatThrownBy(() -> post.transitionTo(new ReviewState.Approved(9L, Instant.now())))
                .isInstanceOf(BizException.class);
        // 失败后状态未被改动
        assertThat(post.getStatus()).isEqualTo(PostStatus.PENDING);
    }

    @Test
    @DisplayName("每一步迁移都会把对应载荷落到实体字段上")
    void shouldCarryPayloadOnTransition() {
        Post post = Post.draft(1L, 1L, "正文", null);
        post.submit(RiskLevel.LOW);

        post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
        assertThat(post.getStatus()).isEqualTo(PostStatus.REVIEWING);
        assertThat(post.getReviewBy()).isEqualTo(9L);

        post.transitionTo(new ReviewState.Rejected("MANUAL_REJECT", "含个人隐私信息"));
        assertThat(post.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(post.getReviewReason()).isEqualTo("含个人隐私信息");
    }

    @Test
    @DisplayName("恢复（申诉通过）清空下架理由，避免作者看到已失效的处罚说明")
    void shouldClearReasonWhenRestored() {
        Post post = Post.draft(1L, 1L, "正文", null);
        post.submit(RiskLevel.LOW);
        post.transitionTo(new ReviewState.Reviewing(9L, Instant.now()));
        post.transitionTo(new ReviewState.Approved(9L, Instant.now()));
        post.transitionTo(new ReviewState.TakenDown(9L, "ABUSE: 人身攻击", Instant.now()));

        post.transitionTo(new ReviewState.Approved(8L, Instant.now()));

        assertThat(post.getReviewReason()).isNull();
        assertThat(post.getTakenDownAt()).isNull();
    }
}
