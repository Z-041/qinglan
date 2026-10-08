package com.qinglan.review.service;

import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.audit.AuditTargetType;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.entity.Post;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.ProposalDispositionRequest;
import com.qinglan.review.dto.VoteResultResponse;
import com.qinglan.review.entity.ArbitrationDecision;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalState;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.ReviewVote;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.review.repository.ReviewVoteRepository;
import com.qinglan.support.TestFixtures;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 表决机制的业务单元测试（P5 二期）。
 *
 * <p>盯三件事：发起时校验与分母快照、投票后的三分支判定（达标执行 / 不可能过半上报 / 继续等）、
 * 以及「处置失败不静默吞掉」（方案 5.3 / 5.5）。执行处置用 operator 哨兵 + 带提案号的理由
 * 记审计（决策 D10）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProposalServiceTest {

    private static final long PROPOSAL_ID = 12L;
    private static final long POST_ID = 7L;
    private static final long AUTHOR_ID = 5L;
    private static final long PROPOSER_ID = 99L;
    private static final long VOTER_ID = 100L;

    @Mock
    private ReviewProposalRepository proposalRepository;

    @Mock
    private ReviewVoteRepository voteRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserService userService;

    @Mock
    private ReviewService reviewService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProposalService proposalService;

    // ---------------------------------------------------------------- 发起

    @Test
    @DisplayName("发起提案：分母取创建时的在册委员会人数快照，并写审计")
    void shouldCreateProposalWithCommitteeSnapshot() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(userService.countCommittee()).thenReturn(5L);
        when(proposalRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ReviewProposal proposal = invocation.getArgument(0);
            TestFixtures.setId(proposal, PROPOSAL_ID);
            return proposal;
        });

        Long proposalId = proposalService.create(
                new CreateProposalRequest(ReviewTargetType.POST, POST_ID, ProposalAction.TAKE_DOWN,
                        ReportReason.ABUSE, "人身攻击"), PROPOSER_ID);

        assertThat(proposalId).isEqualTo(PROPOSAL_ID);
        ArgumentCaptor<ReviewProposal> captor = ArgumentCaptor.forClass(ReviewProposal.class);
        verify(proposalRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSnapshotSize()).isEqualTo(5);
        assertThat(captor.getValue().getStatus()).isEqualTo(ProposalStatus.OPEN);
        assertThat(captor.getValue().getProposerId()).isEqualTo(PROPOSER_ID);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_CREATE, PROPOSER_ID, "TAKE_DOWN（提案 #12）：ABUSE - 人身攻击");
    }

    @Test
    @DisplayName("委员会为空时（超管发起）分母退化为 1，不让快照为 0 而抛错")
    void shouldFallBackToSingleVoterWhenCommitteeEmpty() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(userService.countCommittee()).thenReturn(0L);
        when(proposalRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ReviewProposal proposal = invocation.getArgument(0);
            TestFixtures.setId(proposal, PROPOSAL_ID);
            return proposal;
        });

        proposalService.create(new CreateProposalRequest(ReviewTargetType.POST, POST_ID,
                ProposalAction.TAKE_DOWN, ReportReason.ABUSE, null), PROPOSER_ID);

        ArgumentCaptor<ReviewProposal> captor = ArgumentCaptor.forClass(ReviewProposal.class);
        verify(proposalRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSnapshotSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("动作与目标类型不匹配直接 400，不落库")
    void shouldRejectActionTargetMismatch() {
        assertThatThrownBy(() -> proposalService.create(new CreateProposalRequest(
                ReviewTargetType.COMMENT, POST_ID, ProposalAction.TAKE_DOWN, ReportReason.ABUSE, null),
                PROPOSER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(proposalRepository, userService, auditLogService);
    }

    @Test
    @DisplayName("目标状态与动作不匹配（对已可见帖提「维持下架」）返回专用 400")
    void shouldRejectTargetStateMismatch() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));

        assertThatThrownBy(() -> proposalService.create(new CreateProposalRequest(
                ReviewTargetType.POST, POST_ID, ProposalAction.UPHOLD_TAKEDOWN, ReportReason.ABUSE, null),
                PROPOSER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.PROPOSAL_TARGET_INVALID);
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("同一目标已有进行中提案：唯一键兜底转 409，不静默吞掉")
    void shouldMapDuplicateOpenProposalToConflict() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(approvedPost()));
        when(userService.countCommittee()).thenReturn(5L);
        when(proposalRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_proposal_open_target"));

        assertThatThrownBy(() -> proposalService.create(new CreateProposalRequest(
                ReviewTargetType.POST, POST_ID, ProposalAction.TAKE_DOWN, ReportReason.ABUSE, null),
                PROPOSER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.PROPOSAL_ALREADY_OPEN);
        verifyNoInteractions(auditLogService);
    }

    // ---------------------------------------------------------------- 投票：三分支

    @Test
    @DisplayName("达到阈值：立即以系统哨兵 + 提案号理由执行处置，提案结案为 APPROVED")
    void shouldExecuteDisposalWhenThresholdReached() {
        ReviewProposal proposal = openProposal(6);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(voteRepository.findByProposalIdAndVoterId(PROPOSAL_ID, VOTER_ID)).thenReturn(Optional.empty());
        // 6 人在册，needed = 6/2 + 1 = 4；本次为第 4 票同意 → 达到
        when(voteRepository.findByProposalId(PROPOSAL_ID)).thenReturn(List.of(
                vote(1L, VoteDecision.AGREE), vote(2L, VoteDecision.AGREE),
                vote(3L, VoteDecision.AGREE), vote(VOTER_ID, VoteDecision.AGREE)));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        // 目标状态与动作匹配（TAKE_DOWN 需 APPROVED 帖），执行委托给 ReviewService
        VoteResultResponse result = proposalService.vote(PROPOSAL_ID, VoteDecision.AGREE, VOTER_ID);

        assertThat(result.status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(result.agreeCount()).isEqualTo(4);
        assertThat(result.needed()).isEqualTo(4);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        // 表决通过没有单人裁决者：resolved_by 为 null，于是「是否超管拍板」= resolvedBy != null
        assertThat(proposal.getResolvedBy()).isNull();
        // operator 是系统哨兵 0：不会把任何管理员钉成「原处置人」，申诉复审独立性不受影响（方案 5.6）
        verify(reviewService).takeDown(eq(POST_ID), eq(AuditLogService.SYSTEM_OPERATOR_ID),
                any(ProposalDispositionRequest.class));
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, AuditLogService.SYSTEM_OPERATOR_ID,
                "表决通过（提案 #12，4/6 同意）");
    }

    @Test
    @DisplayName("同意票已不可能过半：立即转 ESCALATED 上报超管，不再接受投票，也不执行处置")
    void shouldEscalateWhenAgreementImpossible() {
        ReviewProposal proposal = openProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(voteRepository.findByProposalIdAndVoterId(PROPOSAL_ID, VOTER_ID)).thenReturn(Optional.empty());
        // 1 同意 3 不同意：needed = 3，未投票者 0 人，最多凑到 1 票 < 3 → 不可能过半
        when(voteRepository.findByProposalId(PROPOSAL_ID)).thenReturn(List.of(
                vote(1L, VoteDecision.AGREE), vote(2L, VoteDecision.DISAGREE),
                vote(3L, VoteDecision.DISAGREE), vote(VOTER_ID, VoteDecision.DISAGREE)));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        VoteResultResponse result = proposalService.vote(PROPOSAL_ID, VoteDecision.DISAGREE, VOTER_ID);

        assertThat(result.status()).isEqualTo(ProposalStatus.ESCALATED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ESCALATED);
        verifyNoInteractions(reviewService);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, AuditLogService.SYSTEM_OPERATOR_ID,
                "同意票已不可能过半（1/4 同意，需 3 票），已上报超管裁决");
    }

    @Test
    @DisplayName("既未达标也未到不可能：保持 OPEN 继续等票")
    void shouldStayOpenWhenStillPossible() {
        ReviewProposal proposal = openProposal(6);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(voteRepository.findByProposalIdAndVoterId(PROPOSAL_ID, VOTER_ID)).thenReturn(Optional.empty());
        // 1 同意 0 不同意（needed = 4）：还剩 5 人可投，仍可能达标
        when(voteRepository.findByProposalId(PROPOSAL_ID)).thenReturn(List.of(vote(VOTER_ID, VoteDecision.AGREE)));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        VoteResultResponse result = proposalService.vote(PROPOSAL_ID, VoteDecision.AGREE, VOTER_ID);

        assertThat(result.status()).isEqualTo(ProposalStatus.OPEN);
        assertThat(result.needed()).isEqualTo(4);
        verifyNoInteractions(reviewService, auditLogService);
    }

    @Test
    @DisplayName("处置无法执行（目标状态已变）：提案结案为 REJECTED 并附原因，不静默吞掉")
    void shouldRejectProposalWhenDisposalFails() {
        ReviewProposal proposal = openProposal(2);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(voteRepository.findByProposalIdAndVoterId(PROPOSAL_ID, VOTER_ID)).thenReturn(Optional.empty());
        // 2 人 2 票同意：needed = 2 → 达到
        when(voteRepository.findByProposalId(PROPOSAL_ID)).thenReturn(List.of(
                vote(1L, VoteDecision.AGREE), vote(VOTER_ID, VoteDecision.AGREE)));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        // 目标已被他人下架：ReviewService 抛非法迁移
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION))
                .when(reviewService).takeDown(eq(POST_ID), anyLong(), any(ProposalDispositionRequest.class));

        VoteResultResponse result = proposalService.vote(PROPOSAL_ID, VoteDecision.AGREE, VOTER_ID);

        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, AuditLogService.SYSTEM_OPERATOR_ID,
                "处置未能执行：" + ErrorCode.ILLEGAL_REVIEW_TRANSITION.message());
    }

    // ---------------------------------------------------------------- 投票：拒绝路径

    @Test
    @DisplayName("非进行中的提案不接受投票")
    void shouldRejectVoteOnResolvedProposal() {
        ReviewProposal proposal = openProposal(4);
        proposal.transitionTo(new ProposalState.Approved(PROPOSER_ID, "已结案"));
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> proposalService.vote(PROPOSAL_ID, VoteDecision.AGREE, VOTER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(voteRepository);
    }

    @Test
    @DisplayName("一人一票：重复投票返回 409")
    void shouldRejectDuplicateVote() {
        ReviewProposal proposal = openProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(voteRepository.findByProposalIdAndVoterId(PROPOSAL_ID, VOTER_ID))
                .thenReturn(Optional.of(vote(VOTER_ID, VoteDecision.AGREE)));

        assertThatThrownBy(() -> proposalService.vote(PROPOSAL_ID, VoteDecision.DISAGREE, VOTER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ALREADY_VOTED);
        verify(voteRepository, never()).save(any());
    }

    @Test
    @DisplayName("提案不存在返回 404")
    void shouldRejectMissingProposal() {
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proposalService.vote(PROPOSAL_ID, VoteDecision.AGREE, VOTER_ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.PROPOSAL_NOT_FOUND);
    }

    // ---------------------------------------------------------------- 超管裁决

    @Test
    @DisplayName("超管裁决准予处置：执行处置并结案为 APPROVED，审计 operator 为超管本人")
    void shouldGrantEscalatedProposal() {
        ReviewProposal proposal = escalatedProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        ProposalStatus status = proposalService.arbitrate(PROPOSAL_ID, ArbitrationDecision.GRANT, "确认违规", 500L);

        assertThat(status).isEqualTo(ProposalStatus.APPROVED);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        // 超管裁决：resolved_by 记他本人（这正是「是否超管拍板」的判定依据）
        assertThat(proposal.getResolvedBy()).isEqualTo(500L);
        // 处置仍以系统哨兵执行（D10）：不会把超管钉成「原处置人」
        verify(reviewService).takeDown(eq(POST_ID), eq(AuditLogService.SYSTEM_OPERATOR_ID),
                any(ProposalDispositionRequest.class));
        // 裁决本身记超管本人，谁拍的板要能查到
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, 500L, "超管裁决（提案 #12）：确认违规");
    }

    @Test
    @DisplayName("超管裁决不予处置：结案为 REJECTED，不执行任何处置")
    void shouldDenyEscalatedProposal() {
        ReviewProposal proposal = escalatedProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        ProposalStatus status = proposalService.arbitrate(PROPOSAL_ID, ArbitrationDecision.DENY, "证据不足", 500L);

        assertThat(status).isEqualTo(ProposalStatus.REJECTED);
        verifyNoInteractions(reviewService);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, 500L, "超管裁决（提案 #12）：证据不足");
    }

    @Test
    @DisplayName("裁决准予但处置无法执行：结案为 REJECTED 并附原因，不静默吞掉")
    void shouldRejectWhenGrantedDisposalFails() {
        ReviewProposal proposal = escalatedProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION))
                .when(reviewService).takeDown(eq(POST_ID), anyLong(), any(ProposalDispositionRequest.class));

        ProposalStatus status = proposalService.arbitrate(PROPOSAL_ID, ArbitrationDecision.GRANT, "确认违规", 500L);

        assertThat(status).isEqualTo(ProposalStatus.REJECTED);
        verify(auditLogService).record(AuditTargetType.POST, POST_ID,
                AuditAction.PROPOSAL_RESOLVE, 500L,
                "处置未能执行：" + ErrorCode.ILLEGAL_REVIEW_TRANSITION.message());
    }

    @Test
    @DisplayName("进行中的提案不接受裁决（只有僵局才需裁决）")
    void shouldRejectArbitrateOnOpenProposal() {
        ReviewProposal proposal = openProposal(4);
        when(proposalRepository.findForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> proposalService.arbitrate(
                PROPOSAL_ID, ArbitrationDecision.GRANT, "直接放行", 500L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        verifyNoInteractions(reviewService, auditLogService);
    }

    // ---------------------------------------------------------------- 辅助

    private static Post approvedPost() {
        return TestFixtures.approvedPost(POST_ID, AUTHOR_ID, 3L, "wx-demo");
    }

    private static ReviewProposal openProposal(int snapshotSize) {
        ReviewProposal proposal = ReviewProposal.open(POST_ID, ProposalAction.TAKE_DOWN, PROPOSER_ID,
                ReportReason.ABUSE, "人身攻击", snapshotSize);
        TestFixtures.setId(proposal, PROPOSAL_ID);
        return proposal;
    }

    private static ReviewProposal escalatedProposal(int snapshotSize) {
        ReviewProposal proposal = openProposal(snapshotSize);
        proposal.transitionTo(new ProposalState.Escalated());
        return proposal;
    }

    private static ReviewVote vote(long voterId, VoteDecision decision) {
        return ReviewVote.of(PROPOSAL_ID, voterId, decision);
    }
}
