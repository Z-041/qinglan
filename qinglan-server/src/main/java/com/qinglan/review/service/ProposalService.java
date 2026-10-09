package com.qinglan.review.service;

import com.qinglan.comment.entity.Comment;
import com.qinglan.comment.repository.CommentRepository;
import com.qinglan.common.audit.AuditAction;
import com.qinglan.common.audit.AuditLogService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.web.CompositeCursor;
import com.qinglan.common.web.CompositeCursor.KeyKind;
import com.qinglan.common.web.CursorCodec;
import com.qinglan.common.web.CursorPage;
import com.qinglan.common.web.TextPreview;
import com.qinglan.post.entity.ContactVisibility;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.review.dto.CreateProposalRequest;
import com.qinglan.review.dto.EscalatedProposalResponse;
import com.qinglan.review.dto.ProposalDetailResponse;
import com.qinglan.review.dto.ProposalDispositionRequest;
import com.qinglan.review.dto.ProposalSummaryResponse;
import com.qinglan.review.dto.ProposalVoteItem;
import com.qinglan.review.dto.VoteResultResponse;
import com.qinglan.review.entity.ArbitrationDecision;
import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalState;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewTargetType;
import com.qinglan.review.entity.ReviewVote;
import com.qinglan.review.entity.VoteDecision;
import com.qinglan.review.entity.VoteTally;
import com.qinglan.review.repository.ReviewProposalRepository;
import com.qinglan.review.repository.ReviewVoteRepository;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处置提案与表决（P5 二期）。
 *
 * <p>承接方案第五、六节：提案的发起、浏览、投票，以及每次投票后的阈值判定（5.3）。
 * 判定与执行处置在**同一事务**内完成，处置逻辑复用 {@link ReviewService}，不另起一套状态迁移
 * （不变量 #5）；operator 记为 {@link AuditLogService#SYSTEM_OPERATOR_ID} 哨兵，理由带提案号
 * （决策 D10）——这样 {@code post.review_by} 是系统值，不会把某个管理员钉成「原处置人」，
 * 申诉复审的独立性不受影响（方案 5.6）。</p>
 *
 * <p>并发：投票与结案都在提案行加锁（{@link ReviewProposalRepository#findForUpdate}）后进行，
 * 保证同一提案的处置只执行一次（方案 5.7）。「同目标只允许一个进行中提案」由可空唯一键兜底，
 * 不依赖先查后插。</p>
 */
@Service
@RequiredArgsConstructor
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 80;
    private static final String KEY_SEPARATOR = ":";

    private final ReviewProposalRepository proposalRepository;
    private final ReviewVoteRepository voteRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final ReviewService reviewService;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ 发起

    /**
     * 发起处置提案。
     *
     * <p>校验：动作与目标类型匹配、目标存在且状态与动作匹配、同目标无进行中提案（唯一键兜底）。
     * 表决分母取**创建时**的在册委员会人数快照，之后委员增减都不改变这条提案的通过线（决策 D6）。</p>
     */
    @Transactional
    public Long create(CreateProposalRequest request, Long proposerId) {
        if (request.action().targetType() != request.targetType()) {
            // 目标类型的真正来源是动作，请求里这个字段只是一道显式自检
            throw new BizException(ErrorCode.INVALID_REQUEST);
        }
        validateTarget(request.action(), request.targetId());

        // 分母快照。下界取 1：超管不在「委员会（ADMIN + REVIEWER）」口径内，若委员会为空时
        // 由超管发起提案，分母会是 0（VoteTally 不接受），而提案人本身至少是一票，
        // 故退化取 1（详见方案十五节二期实施记录）。
        int snapshotSize = (int) Math.max(1L, userService.countCommittee());

        ReviewProposal proposal = ReviewProposal.open(request.targetId(), request.action(), proposerId,
                request.reasonCode(), normalize(request.detail()), snapshotSize);
        ReviewProposal saved;
        try {
            saved = proposalRepository.saveAndFlush(proposal);
        } catch (DataIntegrityViolationException ex) {
            // uk_proposal_open_target：同一目标已有进行中提案，明确回报而不是静默吞掉
            throw new BizException(ErrorCode.PROPOSAL_ALREADY_OPEN);
        }
        auditLogService.record(saved.getTargetType().auditTarget(), saved.getTargetId(),
                AuditAction.PROPOSAL_CREATE, proposerId, describeCreate(saved));
        log.info("发起处置提案 proposalId={} action={} target={}:{}，发起人 proposerId={}",
                saved.getId(), saved.getAction(), saved.getTargetType(), saved.getTargetId(), proposerId);
        return saved.getId();
    }

    // ------------------------------------------------------------------ 列表 / 详情

    /**
     * 提案列表（游标分页）。默认只列进行中（OPEN）；{@code mine} 只看自己发起的（「我的提案」视图）。
     * 行内只给汇总，不给发起人与投票人身份（方案 D8）。
     */
    @Transactional(readOnly = true)
    public CursorPage<ProposalSummaryResponse> page(
            ProposalStatus status, boolean mine, String cursorRaw, Long viewerId) {
        ProposalCursor cursor = parseCursor(cursorRaw);
        ProposalStatus effectiveStatus = status == null ? ProposalStatus.OPEN : status;
        Long proposerFilter = mine ? viewerId : null;

        List<ReviewProposal> rows = proposalRepository.findAdminPage(
                effectiveStatus, proposerFilter, cursor.createdAt(), cursor.id(),
                PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<ReviewProposal> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        Map<Long, List<ReviewVote>> votesByProposal = loadVotes(page);
        Map<String, String> previews = loadPreviews(page);

        List<ProposalSummaryResponse> items = page.stream()
                .map(proposal -> toSummary(proposal,
                        votesByProposal.getOrDefault(proposal.getId(), List.of()),
                        previews.get(key(proposal.getTargetType(), proposal.getTargetId())),
                        viewerId))
                .toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    /** 提案详情：列表行 + 理由与结论。同样只给汇总与「我的票」 */
    @Transactional(readOnly = true)
    public ProposalDetailResponse detail(Long proposalId, Long viewerId) {
        ReviewProposal proposal = requireById(proposalId);
        List<ReviewVote> votes = voteRepository.findByProposalId(proposalId);
        int agree = count(votes, VoteDecision.AGREE);
        int disagree = votes.size() - agree;
        VoteTally tally = VoteTally.of(proposal.getSnapshotSize(), agree, disagree);
        return new ProposalDetailResponse(
                proposal.getId(),
                proposal.getTargetType(),
                proposal.getTargetId(),
                singlePreview(proposal.getTargetType(), proposal.getTargetId()),
                proposal.getAction(),
                proposal.getStatus(),
                proposal.getReasonCode(),
                proposal.getDetail(),
                proposal.getResolution(),
                agree,
                disagree,
                tally.neededCount(),
                tally.votedCount(),
                myDecision(votes, viewerId),
                proposal.getCreatedAt(),
                proposal.getResolvedAt());
    }

    // ------------------------------------------------------------------ 投票

    /**
     * 投票并即时判定（方案 5.3）。
     *
     * <p>判定在同一事务内进行：达阈值即执行处置、不可能过半即上报超管，否则保持等待。
     * 整个过程持提案行锁，保证处置只执行一次。</p>
     */
    @Transactional
    public VoteResultResponse vote(Long proposalId, VoteDecision decision, Long voterId) {
        ReviewProposal proposal = proposalRepository.findForUpdate(proposalId)
                .orElseThrow(() -> new BizException(ErrorCode.PROPOSAL_NOT_FOUND));
        if (!proposal.isOpen()) {
            // 只有进行中的提案接受投票：僵局待裁决、已结案的都不再收票（5.5 / 5.7）
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
        if (voteRepository.findByProposalIdAndVoterId(proposalId, voterId).isPresent()) {
            throw new BizException(ErrorCode.ALREADY_VOTED);
        }

        VoteTally tally;
        try {
            voteRepository.save(ReviewVote.of(proposalId, voterId, decision));
            tally = tallyFromVotes(proposalId, proposal.getSnapshotSize());
        } catch (DataIntegrityViolationException ex) {
            // 并发下同一人重复插入被唯一键拒绝（uk_vote_proposal_voter）
            throw new BizException(ErrorCode.ALREADY_VOTED);
        }

        settle(proposalId, tally);
        // 结案（尤其是执行处置那一步）可能已清过一级缓存，故统一在这里现取一次状态
        ProposalStatus status = requireById(proposalId).getStatus();
        log.info("提案投票 proposalId={} voterId={} decision={} → 状态={}", proposalId, voterId, decision, status);
        return new VoteResultResponse(status,
                tally.agreeCount(), tally.disagreeCount(), tally.neededCount(), decision);
    }

    // ------------------------------------------------------------------ 超管裁决

    /**
     * 待裁决列表（僵局提案）：仅超管可见，带每一票的明细（决策 D8）。
     *
     * <p>单独一条查询而不是复用 {@code page(ESCALATED)}：委员会也能看 ESCALATED 的汇总，
     * 但只有超管能看「谁投了什么」，两条路径的出参本就不同。</p>
     */
    @Transactional(readOnly = true)
    public CursorPage<EscalatedProposalResponse> escalatedPage(String cursorRaw) {
        ProposalCursor cursor = parseCursor(cursorRaw);
        List<ReviewProposal> rows = proposalRepository.findAdminPage(
                ProposalStatus.ESCALATED, null, cursor.createdAt(), cursor.id(),
                PageRequest.of(0, DEFAULT_PAGE_SIZE + 1));

        boolean hasMore = rows.size() > DEFAULT_PAGE_SIZE;
        List<ReviewProposal> page = hasMore ? List.copyOf(rows.subList(0, DEFAULT_PAGE_SIZE)) : rows;
        if (page.isEmpty()) {
            return CursorPage.empty();
        }
        Map<Long, List<ReviewVote>> votesByProposal = loadVotes(page);
        Map<String, String> previews = loadPreviews(page);

        List<EscalatedProposalResponse> items = page.stream()
                .map(proposal -> toEscalated(proposal,
                        votesByProposal.getOrDefault(proposal.getId(), List.of()),
                        previews.get(key(proposal.getTargetType(), proposal.getTargetId()))))
                .toList();
        String nextCursor = hasMore ? formatCursor(page.get(page.size() - 1)) : null;
        return CursorPage.of(items, nextCursor);
    }

    /**
     * 超管裁决（方案 5.4）：准予处置则执行处置并结案为 APPROVED，不予处置则结案为 REJECTED。
     *
     * <p>单人动作，但必须填理由；全程留痕（{@code PROPOSAL_RESOLVE}，operator 为超管本人，
     * 与表决执行时的系统哨兵相区分——谁拍的板要能查到）。处置失败同样不静默吞掉，结案为 REJECTED。</p>
     */
    @Transactional
    public ProposalStatus arbitrate(
            Long proposalId, ArbitrationDecision decision, String reason, Long operatorId) {
        ReviewProposal proposal = proposalRepository.findForUpdate(proposalId)
                .orElseThrow(() -> new BizException(ErrorCode.PROPOSAL_NOT_FOUND));
        if (!proposal.isEscalated()) {
            // 只有僵局提案需要裁决：进行中的应继续投票，已结案的不能被推翻（终态不可再变）
            throw new BizException(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        }
        String resolution = "超管裁决（提案 #%d）：%s".formatted(proposalId, reason.trim());

        if (decision == ArbitrationDecision.GRANT) {
            String failure = executeDisposal(proposal, resolution);
            ReviewProposal fresh = requireById(proposalId);
            if (failure == null) {
                fresh.transitionTo(new ProposalState.Approved(operatorId, resolution));
                auditResolve(fresh, operatorId, resolution);
            } else {
                String failReason = "处置未能执行：" + failure;
                fresh.transitionTo(new ProposalState.Rejected(operatorId, failReason));
                auditResolve(fresh, operatorId, failReason);
            }
        } else {
            ReviewProposal rejected = requireById(proposalId);
            rejected.transitionTo(new ProposalState.Rejected(operatorId, resolution));
            auditResolve(rejected, operatorId, resolution);
        }
        ProposalStatus status = requireById(proposalId).getStatus();
        log.info("超管裁决 proposalId={} decision={} → 状态={}，操作人 operatorId={}",
                proposalId, decision, status, operatorId);
        return status;
    }

    // ------------------------------------------------------------------ 判定与结案

    private void settle(Long proposalId, VoteTally tally) {
        switch (tally.decide()) {
            case APPROVED -> approveByVote(proposalId, tally);
            case ESCALATED -> escalate(proposalId, tally);
            case OPEN, REJECTED -> {
                // 继续等票（decide() 不产生 REJECTED，显式列全仅为穷尽枚举）
            }
        }
    }

    /**
     * 达到阈值：执行处置并结案为 APPROVED。
     *
     * <p>处置失败（目标状态在表决期间已变）不静默吞掉——按方案 5.5 结案为 REJECTED 并附原因。
     * 执行处置会触发批量 UPDATE 的 {@code clearAutomatically}，故结案前必须重新按 id 取一次提案，
     * 否则拿到的是已脱离持久化上下文的引用，改它不会落库。</p>
     */
    private void approveByVote(Long proposalId, VoteTally tally) {
        ReviewProposal proposal = requireById(proposalId);
        String resolution = "表决通过（提案 #%d，%d/%d 同意）"
                .formatted(proposalId, tally.agreeCount(), tally.snapshotSize());

        String failure = executeDisposal(proposal, resolution);
        ReviewProposal fresh = requireById(proposalId);
        if (failure == null) {
            // resolved_by 只记「裁决人」：表决通过是委员会的共同结论，没有单人裁决者，故为 null
            // （发起人已有 proposer_id，重复记一遍反而让「是否超管拍板」变得无法判定）
            fresh.transitionTo(new ProposalState.Approved(null, resolution));
            auditResolve(fresh, AuditLogService.SYSTEM_OPERATOR_ID, resolution);
        } else {
            String reason = "处置未能执行：" + failure;
            fresh.transitionTo(new ProposalState.Rejected(null, reason));
            auditResolve(fresh, AuditLogService.SYSTEM_OPERATOR_ID, reason);
        }
    }

    /** 同意票已不可能过半：转超管裁决，不再接受投票（决策 D7）。目标仍被占用，防止僵局期间再提一条 */
    private void escalate(Long proposalId, VoteTally tally) {
        ReviewProposal proposal = requireById(proposalId);
        proposal.transitionTo(new ProposalState.Escalated());
        String reason = "同意票已不可能过半（%d/%d 同意，需 %d 票），已上报超管裁决"
                .formatted(tally.agreeCount(), tally.snapshotSize(), tally.neededCount());
        auditResolve(proposal, AuditLogService.SYSTEM_OPERATOR_ID, reason);
    }

    /**
     * 执行处置（方案 5.5）。复用 {@link ReviewService}，动作与目标类型一一对应。
     *
     * @return 成功返回 null；失败返回原因文本（目标状态已变等），由调用方结案为 REJECTED
     */
    private String executeDisposal(ReviewProposal proposal, String resolution) {
        long operatorId = AuditLogService.SYSTEM_OPERATOR_ID;
        try {
            switch (proposal.getAction()) {
                case TAKE_DOWN -> reviewService.takeDown(proposal.getTargetId(), operatorId,
                        new ProposalDispositionRequest(proposal.getReasonCode(), proposal.getDetail()));
                case UPHOLD_TAKEDOWN -> reviewService.upholdReports(
                        proposal.getTargetId(), operatorId, resolution);
                case HIDE_CONTACT -> reviewService.hideContact(
                        proposal.getTargetId(), operatorId, resolution);
                case REMOVE_COMMENT -> reviewService.removeComment(
                        proposal.getTargetId(), operatorId, resolution);
            }
            return null;
        } catch (BizException ex) {
            log.warn("提案 #{} 的处置未能执行：{}", proposal.getId(), ex.getMessage());
            return ex.getMessage();
        }
    }

    private void auditResolve(ReviewProposal proposal, Long operatorId, String reason) {
        auditLogService.record(proposal.getTargetType().auditTarget(), proposal.getTargetId(),
                AuditAction.PROPOSAL_RESOLVE, operatorId, reason);
    }

    // ------------------------------------------------------------------ 校验

    /** 动作与目标状态必须匹配：对 PENDING 帖不能提「下架」、对可见帖不能提「维持下架」等（7.4） */
    private void validateTarget(ProposalAction action, Long targetId) {
        switch (action) {
            case TAKE_DOWN -> {
                if (requirePost(targetId).getStatus() != PostStatus.APPROVED) {
                    throw new BizException(ErrorCode.PROPOSAL_TARGET_INVALID);
                }
            }
            case UPHOLD_TAKEDOWN -> {
                if (requirePost(targetId).getStatus() != PostStatus.TAKEN_DOWN) {
                    throw new BizException(ErrorCode.PROPOSAL_TARGET_INVALID);
                }
            }
            case HIDE_CONTACT -> {
                if (requirePost(targetId).getContactVisibility() == ContactVisibility.HIDDEN) {
                    throw new BizException(ErrorCode.PROPOSAL_TARGET_INVALID);
                }
            }
            case REMOVE_COMMENT -> {
                if (requireComment(targetId).isRemoved()) {
                    throw new BizException(ErrorCode.PROPOSAL_TARGET_INVALID);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 内部方法

    private ReviewProposal requireById(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .orElseThrow(() -> new BizException(ErrorCode.PROPOSAL_NOT_FOUND));
    }

    private Post requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    private Comment requireComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Map<Long, List<ReviewVote>> loadVotes(List<ReviewProposal> page) {
        List<Long> ids = page.stream().map(ReviewProposal::getId).toList();
        Map<Long, List<ReviewVote>> result = new HashMap<>();
        for (ReviewVote vote : voteRepository.findByProposalIdIn(ids)) {
            result.computeIfAbsent(vote.getProposalId(), ignored -> new ArrayList<>()).add(vote);
        }
        return result;
    }

    /** 一页之内批量装配目标内容摘要，杜绝逐行查询（N+1） */
    private Map<String, String> loadPreviews(List<ReviewProposal> page) {
        Map<String, String> result = new HashMap<>();
        List<Long> postIds = targetIds(page, ReviewTargetType.POST);
        if (!postIds.isEmpty()) {
            postRepository.findAllById(postIds)
                    .forEach(post -> result.put(key(ReviewTargetType.POST, post.getId()), preview(post.getContent())));
        }
        List<Long> commentIds = targetIds(page, ReviewTargetType.COMMENT);
        if (!commentIds.isEmpty()) {
            commentRepository.findAllById(commentIds)
                    .forEach(comment -> result.put(key(ReviewTargetType.COMMENT, comment.getId()),
                            preview(comment.getContent())));
        }
        return result;
    }

    private String singlePreview(ReviewTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST -> postRepository.findById(targetId).map(post -> preview(post.getContent())).orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(comment -> preview(comment.getContent())).orElse(null);
        };
    }

    private List<Long> targetIds(List<ReviewProposal> page, ReviewTargetType targetType) {
        return page.stream()
                .filter(proposal -> proposal.getTargetType() == targetType)
                .map(ReviewProposal::getTargetId)
                .toList();
    }

    private ProposalSummaryResponse toSummary(ReviewProposal proposal, List<ReviewVote> votes,
                                              String preview, Long viewerId) {
        int agree = count(votes, VoteDecision.AGREE);
        int disagree = votes.size() - agree;
        VoteTally tally = VoteTally.of(proposal.getSnapshotSize(), agree, disagree);
        return new ProposalSummaryResponse(
                proposal.getId(),
                proposal.getTargetType(),
                proposal.getTargetId(),
                preview,
                proposal.getAction(),
                proposal.getStatus(),
                agree,
                disagree,
                tally.neededCount(),
                tally.votedCount(),
                myDecision(votes, viewerId),
                proposal.getCreatedAt());
    }

    /** 待裁决行：汇总 + 每一票的明细（仅超管端点使用，决策 D8） */
    private EscalatedProposalResponse toEscalated(ReviewProposal proposal, List<ReviewVote> votes,
                                                  String preview) {
        int agree = count(votes, VoteDecision.AGREE);
        int disagree = votes.size() - agree;
        VoteTally tally = VoteTally.of(proposal.getSnapshotSize(), agree, disagree);
        List<ProposalVoteItem> items = votes.stream()
                .map(vote -> new ProposalVoteItem(vote.getVoterId(), vote.getDecision(), vote.getCreatedAt()))
                .toList();
        return new EscalatedProposalResponse(
                proposal.getId(),
                proposal.getTargetType(),
                proposal.getTargetId(),
                preview,
                proposal.getAction(),
                proposal.getStatus(),
                agree,
                disagree,
                tally.neededCount(),
                tally.votedCount(),
                proposal.getCreatedAt(),
                items);
    }

    /** 从库里现取票计票（投票后判定用；save 之后的查询会先自动 flush，故本次投票已被计入） */
    private VoteTally tallyFromVotes(Long proposalId, int snapshotSize) {
        List<ReviewVote> votes = voteRepository.findByProposalId(proposalId);
        int agree = count(votes, VoteDecision.AGREE);
        return VoteTally.of(snapshotSize, agree, votes.size() - agree);
    }

    private static int count(List<ReviewVote> votes, VoteDecision decision) {
        return (int) votes.stream().filter(vote -> vote.getDecision() == decision).count();
    }

    private VoteDecision myDecision(List<ReviewVote> votes, Long viewerId) {
        return votes.stream()
                .filter(vote -> vote.getVoterId().equals(viewerId))
                .map(ReviewVote::getDecision)
                .findFirst()
                .orElse(null);
    }

    private String preview(String content) {
        return TextPreview.of(content, PREVIEW_LENGTH);
    }

    private String key(ReviewTargetType targetType, Long targetId) {
        return targetType + KEY_SEPARATOR + targetId;
    }

    private String describeCreate(ReviewProposal proposal) {
        String base = "%s（提案 #%d）：%s".formatted(
                proposal.getAction().name(), proposal.getId(), proposal.getReasonCode().name());
        String detail = proposal.getDetail();
        return detail == null ? base : base + " - " + detail;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProposalCursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ProposalCursor(null, null);
        }
        long[] key = CompositeCursor.parse(raw, KeyKind.MICROS, KeyKind.POSITIVE_LONG);
        return new ProposalCursor(CursorCodec.decode(key[0]), key[1]);
    }

    private String formatCursor(ReviewProposal proposal) {
        return CompositeCursor.format(CursorCodec.encode(proposal.getCreatedAt()), proposal.getId());
    }

    /** 提案列表游标：与 {@link ReviewProposalRepository#findAdminPage} 的排序键逐一对应 */
    private record ProposalCursor(Instant createdAt, Long id) {
    }
}
