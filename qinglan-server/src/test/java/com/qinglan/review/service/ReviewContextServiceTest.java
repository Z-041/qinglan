package com.qinglan.review.service;

import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminReviewContextResponse;
import com.qinglan.review.entity.Report;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewContextRepository;
import com.qinglan.user.entity.User;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewContextServiceTest {

    private static final Long POST_ID = 5L;
    private static final Long AUTHOR_ID = 42L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserService userService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReviewContextRepository reviewContextRepository;

    @InjectMocks
    private ReviewContextService reviewContextService;

    @Test
    @DisplayName("返回上下文字段，且不含作者与举报人身份")
    void shouldBuildContextForPost() {
        Post post = post(PostStatusForTest.APPROVED);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(userService.requireById(AUTHOR_ID)).thenReturn(user(96));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING))
                .thenReturn(List.of(report(11L, ReportReason.ABUSE), report(12L, ReportReason.ABUSE)));
        when(reportRepository.findDistinctReporterIds(ReviewTargetType.POST, POST_ID))
                .thenReturn(List.of(11L, 12L, 13L));
        when(reportRepository.countReportersWithOtherReports(any(), eq(ReviewTargetType.POST), eq(POST_ID)))
                .thenReturn(2L);
        when(reviewContextRepository.countAuthorViolations(AUTHOR_ID)).thenReturn(3L);

        AdminReviewContextResponse response = reviewContextService.context(ReviewTargetType.POST, POST_ID);

        assertThat(response.authorCreditScore()).isEqualTo(96);
        assertThat(response.authorViolationCount()).isEqualTo(3L);
        assertThat(response.reportCount()).isEqualTo(2);
        assertThat(response.distinctReporterCount()).isEqualTo(3);
        assertThat(response.repeatedReporterCount()).isEqualTo(2L);
        assertThat(response.reasons()).containsExactly(ReportReason.ABUSE);
        assertThat(response.targetStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("目标不存在抛 404")
    void shouldRejectMissingPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewContextService.context(ReviewTargetType.POST, POST_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("无举报人时不查重复举报人，直接为 0")
    void shouldSkipRepeatQueryWhenNoReporters() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post(PostStatusForTest.APPROVED)));
        when(userService.requireById(AUTHOR_ID)).thenReturn(user(100));
        when(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReviewTargetType.POST, POST_ID, ReportStatus.PENDING)).thenReturn(List.of());
        when(reportRepository.findDistinctReporterIds(ReviewTargetType.POST, POST_ID)).thenReturn(List.of());
        when(reviewContextRepository.countAuthorViolations(AUTHOR_ID)).thenReturn(0L);

        AdminReviewContextResponse response = reviewContextService.context(ReviewTargetType.POST, POST_ID);

        assertThat(response.repeatedReporterCount()).isZero();
        assertThat(response.reasons()).isEmpty();
    }

    private Post post(PostStatusForTest status) {
        Post post = Post.draft(AUTHOR_ID, 1L, "内容", null);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        ReflectionTestUtils.setField(post, "status",
                com.qinglan.post.entity.PostStatus.valueOf(status.name()));
        return post;
    }

    private User user(int credit) {
        User user = User.register("h", "sh", "enc", "a".repeat(32));
        ReflectionTestUtils.setField(user, "id", AUTHOR_ID);
        ReflectionTestUtils.setField(user, "creditScore", credit);
        return user;
    }

    private Report report(Long id, ReportReason reason) {
        Report report = Report.submit(ReviewTargetType.POST, POST_ID, id, reason, null);
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    /** 避免测试里再引入一个 import，直接用帖子状态的字符串名 */
    private enum PostStatusForTest { APPROVED }
}
