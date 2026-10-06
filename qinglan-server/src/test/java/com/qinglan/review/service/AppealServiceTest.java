package com.qinglan.review.service;

import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.notify.NotificationMessage;
import com.qinglan.common.notify.NotificationPort;
import com.qinglan.common.notify.NotificationType;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.ReviewState;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.config.ReviewProperties;
import com.qinglan.review.entity.Appeal;
import com.qinglan.review.entity.AppealStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.AppealRepository;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 申诉与复审的单元测试。
 *
 * <p>盯三条：只有本人且确已下架的帖子能申诉、每帖限一次、复审人 ≠ 原处置人。</p>
 */
@ExtendWith(MockitoExtension.class)
class AppealServiceTest {

    private static final long POST_ID = 1L;
    private static final long AUTHOR_ID = 7L;
    private static final long OPERATOR_ID = 99L;
    private static final long REVIEWER_ID = 100L;
    private static final long APPEAL_ID = 500L;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReviewService reviewService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationPort notificationPort;

    private AppealService appealService;

    @BeforeEach
    void setUp() {
        appealService = new AppealService(appealRepository, postRepository, reviewService,
                new ReviewProperties(3, Duration.ofDays(7)), auditLogService, userService, notificationPort);
    }

    // ---------------------------------------------------------------- 申诉提交

    @Test
    @DisplayName("作者对已下架帖子申诉成功，落 PENDING 并去除理由首尾空白")
    void shouldSubmitAppealForTakenDownPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));
        when(appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, POST_ID))
                .thenReturn(Optional.empty());

        appealService.submit(AUTHOR_ID, POST_ID, "  内容没有违规  ");

        ArgumentCaptor<Appeal> captor = ArgumentCaptor.forClass(Appeal.class);
        verify(appealRepository).saveAndFlush(captor.capture());
        Appeal saved = captor.getValue();
        assertThat(saved.getTargetType()).isEqualTo(ReviewTargetType.POST);
        assertThat(saved.getTargetId()).isEqualTo(POST_ID);
        assertThat(saved.getAppellantId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getReason()).isEqualTo("内容没有违规");
        assertThat(saved.getStatus()).isEqualTo(AppealStatus.PENDING);
        assertThat(saved.isRestored()).isFalse();
    }

    @Test
    @DisplayName("封禁/黑名单用户不能申诉：写入口重查账号可用性，不依赖登录时的校验")
    void shouldRejectAppealFromUnusableUser() {
        when(userService.requireUsableById(AUTHOR_ID))
                .thenThrow(new BizException(ErrorCode.USER_BANNED));

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "我要申诉"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
        verifyNoInteractions(postRepository, appealRepository);
    }

    @Test
    @DisplayName("并发下唯一键兜底：插入被拒同样回 409，而不是落到全局兜底变 500")
    void shouldMapUniqueViolationToConflict() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));
        when(appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, POST_ID))
                .thenReturn(Optional.empty());
        when(appealRepository.saveAndFlush(any(Appeal.class)))
                .thenThrow(new DataIntegrityViolationException("uk_appeal_target"));

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "同时提交"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.APPEAL_ALREADY_SUBMITTED);
    }

    @Test
    @DisplayName("非作者不能替他人申诉（不变量：只有当事人可申诉）")
    void shouldRejectAppealFromNonAuthor() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));

        assertThatThrownBy(() -> appealService.submit(12345L, POST_ID, "不关我的事"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_CONTENT_OWNER);
        verify(appealRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("帖子不存在时申诉按「帖子不存在」处理")
    void shouldRejectAppealForMissingPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "误判"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("未下架的帖子没有可申诉的处置，按非法状态拒绝")
    void shouldRejectAppealForApprovedPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "我要申诉"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
    }

    @Test
    @DisplayName("超过申诉期的申诉被拒绝（7 日内有效）")
    void shouldRejectExpiredAppeal() {
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(takenDownPost(Instant.now().minus(Duration.ofDays(8)))));

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "来晚了"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.APPEAL_WINDOW_EXPIRED);
    }

    @Test
    @DisplayName("每帖限一次：已有申诉记录时再次申诉返回 409")
    void shouldRejectSecondAppealOnSamePost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));
        when(appealRepository.findByTargetTypeAndTargetId(ReviewTargetType.POST, POST_ID))
                .thenReturn(Optional.of(appeal(AppealStatus.REJECTED)));

        assertThatThrownBy(() -> appealService.submit(AUTHOR_ID, POST_ID, "再试一次"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.APPEAL_ALREADY_SUBMITTED);
    }

    // ---------------------------------------------------------------- 复审

    @Test
    @DisplayName("复审通过：申诉置 ACCEPTED，并委托恢复帖子可见，全程留痕")
    void shouldAcceptAppealAndRestorePost() {
        Appeal appeal = appeal(AppealStatus.PENDING);
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));

        appealService.accept(APPEAL_ID, REVIEWER_ID, "确系误判");

        assertThat(appeal.getStatus()).isEqualTo(AppealStatus.ACCEPTED);
        assertThat(appeal.getReviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(appeal.isRestored()).isTrue();
        verify(reviewService).restore(POST_ID, REVIEWER_ID, "确系误判");
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.APPEAL_REVIEW, REVIEWER_ID, "ACCEPTED: 确系误判");
    }

    @Test
    @DisplayName("申诉复审结果通知申诉人本人，且通知里只有一个跳转链接、不含任何身份字段")
    void shouldNotifyAppellantOnAppealReview() {
        Appeal appeal = appeal(AppealStatus.PENDING);
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));

        appealService.accept(APPEAL_ID, REVIEWER_ID, "确系误判");

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationPort).notify(eq(AUTHOR_ID), eq(REVIEWER_ID), captor.capture());
        NotificationMessage message = captor.getValue();
        assertThat(message.type()).isEqualTo(NotificationType.REVIEW_RESULT);
        assertThat(message.link()).isEqualTo("/posts/" + POST_ID);
        assertThat(message.title()).isNotBlank();
    }

    @Test
    @DisplayName("复审不通过：维持原判，不触碰内容状态")
    void shouldRejectAppealAndKeepPostTakenDown() {
        Appeal appeal = appeal(AppealStatus.PENDING);
        Post post = takenDownPost(Instant.now());
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));

        appealService.reject(APPEAL_ID, REVIEWER_ID, "维持原判");

        assertThat(appeal.getStatus()).isEqualTo(AppealStatus.REJECTED);
        assertThat(appeal.isRestored()).isFalse();
        assertThat(post.getStatus()).isEqualTo(PostStatus.TAKEN_DOWN);
        verifyNoInteractions(reviewService);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.APPEAL_REVIEW, REVIEWER_ID, "REJECTED: 维持原判");
    }

    @Test
    @DisplayName("复审人不得是原处置人：同一管理员复核自己的裁决被拒绝且不留审计")
    void shouldRejectSelfReview() {
        Appeal appeal = appeal(AppealStatus.PENDING);
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));

        assertThatThrownBy(() -> appealService.accept(APPEAL_ID, OPERATOR_ID, "我自己改判"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.SELF_REVIEW_FORBIDDEN);
        assertThat(appeal.getStatus()).isEqualTo(AppealStatus.PENDING);
        verifyNoInteractions(reviewService, auditLogService);
    }

    @Test
    @DisplayName("申诉不存在时复审按「申诉不存在」处理")
    void shouldRejectReviewOfMissingAppeal() {
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appealService.accept(APPEAL_ID, REVIEWER_ID, "通过"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.APPEAL_NOT_FOUND);
        verifyNoInteractions(reviewService, auditLogService);
    }

    @Test
    @DisplayName("已复审过的申诉不能再次复审（并发下两位管理员同时点同一条）")
    void shouldRejectReviewingTwice() {
        when(appealRepository.findById(APPEAL_ID)).thenReturn(Optional.of(appeal(AppealStatus.ACCEPTED)));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(takenDownPost(Instant.now())));

        assertThatThrownBy(() -> appealService.reject(APPEAL_ID, REVIEWER_ID, "再驳一次"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(reviewService, auditLogService);
    }

    // ------------------------------------------------------------------ 辅助

    private static Post approvedPost() {
        return TestFixtures.approvedPost(POST_ID, AUTHOR_ID, 3L, null);
    }

    /** 下架手为 OPERATOR_ID，下架时间可控，用于验申诉期限 */
    private static Post takenDownPost(Instant takenDownAt) {
        Post post = approvedPost();
        post.transitionTo(new ReviewState.TakenDown(OPERATOR_ID, "ABUSE: 人身攻击", takenDownAt));
        return post;
    }

    private static Appeal appeal(AppealStatus status) {
        Appeal appeal = Appeal.submit(ReviewTargetType.POST, POST_ID, AUTHOR_ID, "原申诉理由");
        if (status == AppealStatus.ACCEPTED) {
            appeal.accept(REVIEWER_ID);
        } else if (status == AppealStatus.REJECTED) {
            appeal.reject(REVIEWER_ID);
        }
        TestFixtures.setId(appeal, APPEAL_ID);
        return appeal;
    }
}
