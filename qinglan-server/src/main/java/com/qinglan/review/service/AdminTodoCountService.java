package com.qinglan.review.service;

import com.qinglan.common.security.AdminPermission;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.AdminTodoCountsResponse;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportStatus;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.repository.ReportRepository;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 待办计数（阶段 B）：按当前登录用户的权限点裁剪分组，无权限的组不返回。
 *
 * <p>刻意用 COUNT 而非拉数据：轮询接口 30 秒一次，只允许返回几个数字。分组与权限点的对应由
 * 本服务一处决定，端点只做 {@code MODERATION_VIEW} 的门禁（三档都有）——组内再按权限点裁剪，
 * 例如评议员看不到「僵局待裁决」（无 {@code PROPOSAL_ARBITRATE}）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminTodoCountService {

    /** 待审口径：与看板一致（PENDING + REVIEWING），避免「看板数字」与「队列条数」对不上 */
    private static final List<PostStatus> PENDING_STATUSES =
            List.of(PostStatus.PENDING, PostStatus.REVIEWING);

    private final UserService userService;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final ReviewProposalRepository proposalRepository;

    @Transactional(readOnly = true)
    public AdminTodoCountsResponse counts(Long userId) {
        Set<AdminPermission> permissions = userService.requireById(userId).getRole().permissions();
        return new AdminTodoCountsResponse(
                has(permissions, AdminPermission.MODERATION_VIEW)
                        ? postRepository.countByStatusIn(PENDING_STATUSES) : null,
                has(permissions, AdminPermission.MODERATION_VIEW)
                        ? pendingReportTargets() : null,
                has(permissions, AdminPermission.PROPOSAL_VOTE)
                        ? proposalRepository.countByStatus(ProposalStatus.OPEN) : null,
                has(permissions, AdminPermission.PROPOSAL_ARBITRATE)
                        ? proposalRepository.countByStatus(ProposalStatus.ESCALATED) : null);
    }

    /**
     * 待处理举报的**目标数**：按目标类型分别去重后求和，与审核队列行数一致。
     *
     * <p>不能直接数举报条数——同一目标被多人举报时队列里只有一行；也不能跨类型对 target_id
     * 一次去重——帖子 #7 与评论 #7 会撞号。</p>
     */
    private long pendingReportTargets() {
        return reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.POST)
                + reportRepository.countDistinctTargets(ReportStatus.PENDING, ReviewTargetType.COMMENT);
    }

    private boolean has(Set<AdminPermission> permissions, AdminPermission permission) {
        return permissions.contains(permission);
    }
}
