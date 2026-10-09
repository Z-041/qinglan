package com.qinglan.review.service;

import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminTodoCountsResponse;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.entity.User;
import com.qinglan.user.entity.UserRole;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待办计数的单元测试（阶段 B）。
 *
 * <p>盯两件事：① 无权限的分组**不返回**（null，而非 0）；② 待处理举报数是**目标数**而不是举报条数。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminTodoCountServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private UserService userService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReviewProposalRepository proposalRepository;

    @InjectMocks
    private AdminTodoCountService adminTodoCountService;

    @Test
    @DisplayName("超管有全部权限：四组计数齐备")
    void shouldReturnAllGroupsForSuperAdmin() {
        when(userService.requireById(USER_ID)).thenReturn(user(UserRole.SUPER_ADMIN));
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(3L);
        when(reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.POST)).thenReturn(2L);
        when(reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.COMMENT)).thenReturn(1L);
        when(proposalRepository.countByStatus(ProposalStatus.OPEN)).thenReturn(4L);
        when(proposalRepository.countByStatus(ProposalStatus.ESCALATED)).thenReturn(5L);

        AdminTodoCountsResponse counts = adminTodoCountService.counts(USER_ID);

        assertThat(counts.pendingPosts()).isEqualTo(3L);
        // 目标数 = 帖子 + 评论两类各去重后求和
        assertThat(counts.pendingReports()).isEqualTo(3L);
        assertThat(counts.proposable()).isEqualTo(4L);
        assertThat(counts.escalated()).isEqualTo(5L);
    }

    @Test
    @DisplayName("评议员无 PROPOSAL_ARBITRATE：僵局待裁决组不返回（null），其余齐备")
    void shouldOmitEscalatedForReviewer() {
        when(userService.requireById(USER_ID)).thenReturn(user(UserRole.REVIEWER));
        when(postRepository.countByStatusIn(anyCollection())).thenReturn(1L);
        when(reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.POST)).thenReturn(0L);
        when(reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.COMMENT)).thenReturn(0L);
        when(proposalRepository.countByStatus(ProposalStatus.OPEN)).thenReturn(2L);

        AdminTodoCountsResponse counts = adminTodoCountService.counts(USER_ID);

        assertThat(counts.pendingPosts()).isEqualTo(1L);
        assertThat(counts.pendingReports()).isZero();
        assertThat(counts.proposable()).isEqualTo(2L);
        // 无权限的组是 null，不是 0（响应里会整个缺席）
        assertThat(counts.escalated()).isNull();
        verify(proposalRepository, org.mockito.Mockito.never()).countByStatus(ProposalStatus.ESCALATED);
    }

    @Test
    @DisplayName("委员会成员（无 MODERATION_VIEW 的学生）不该进这个端点，但服务层也按权限裁剪")
    void shouldNotQueryAnythingForStudent() {
        when(userService.requireById(USER_ID)).thenReturn(user(UserRole.STUDENT));

        AdminTodoCountsResponse counts = adminTodoCountService.counts(USER_ID);

        assertThat(counts.pendingPosts()).isNull();
        assertThat(counts.pendingReports()).isNull();
        assertThat(counts.proposable()).isNull();
        assertThat(counts.escalated()).isNull();
        verifyNoInteractions(postRepository, reportRepository, proposalRepository);
    }

    private User user(UserRole role) {
        User user = User.register("h", "sh", "enc", "a".repeat(32));
        TestFixtures.setId(user, USER_ID);
        user.changeRole(role);
        return user;
    }
}
