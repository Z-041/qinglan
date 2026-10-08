package com.qinglan.review.repository;

import com.qinglan.review.entity.ProposalAction;
import com.qinglan.review.entity.ProposalState;
import com.qinglan.review.entity.ProposalStatus;
import com.qinglan.review.entity.ReportReason;
import com.qinglan.review.entity.ReviewProposal;
import com.qinglan.review.entity.ReviewVote;
import com.qinglan.review.entity.VoteDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提案与投票的持久层测试：真实 MySQL（本机 qinglan_test），不 mock 数据库行为。
 *
 * <p>重点验证两条唯一约束的最终防线：{@code uk_proposal_open_target}（同目标只允许一个进行中提案，
 * 由可空列模拟部分唯一索引）与 {@code uk_vote_proposal_voter}（一人一票）。Mock 掉仓储的单测
 * 测不到任何数据库约束，这类断言只能在真实库上做。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReviewProposalRepositoryTest {

    private static final long PROPOSER_ID = 99L;

    @Autowired
    private ReviewProposalRepository proposalRepository;

    @Autowired
    private ReviewVoteRepository voteRepository;

    @Test
    @DisplayName("同一目标不能有两个进行中提案：第二个被唯一键拒绝")
    void shouldRejectSecondOpenProposalOnSameTarget() {
        proposalRepository.saveAndFlush(openFor(7L));

        assertThatThrownBy(() -> proposalRepository.saveAndFlush(openFor(7L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同目标可各有一个进行中提案，唯一键不影响正常提案")
    void shouldAllowOpenProposalsOnDifferentTargets() {
        proposalRepository.saveAndFlush(openFor(7L));
        proposalRepository.saveAndFlush(openFor(8L));

        assertThat(proposalRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("前一条提案结案（open_target_key 置 NULL）后，同一目标可以再提案")
    void shouldAllowNewProposalAfterPreviousResolved() {
        ReviewProposal first = proposalRepository.saveAndFlush(openFor(7L));
        first.transitionTo(new ProposalState.Approved(PROPOSER_ID, "表决通过"));
        proposalRepository.saveAndFlush(first);

        // 结案释放目标键：恢复可见后又被举报的场景必须还能再提一条
        proposalRepository.saveAndFlush(openFor(7L));

        assertThat(proposalRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("一人一票：同一人对同一提案投两次被唯一键拒绝")
    void shouldRejectSecondVoteFromSameVoter() {
        Long proposalId = proposalRepository.saveAndFlush(openFor(7L)).getId();
        voteRepository.save(ReviewVote.of(proposalId, 100L, VoteDecision.AGREE));

        // id 由数据库自增（IDENTITY），INSERT 在 save 当刻执行，唯一键冲突也在此抛出
        assertThatThrownBy(() -> voteRepository.save(ReviewVote.of(proposalId, 100L, VoteDecision.DISAGREE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同人可对同一提案各投一票")
    void shouldAllowDifferentVoters() {
        Long proposalId = proposalRepository.saveAndFlush(openFor(7L)).getId();
        voteRepository.save(ReviewVote.of(proposalId, 100L, VoteDecision.AGREE));
        voteRepository.save(ReviewVote.of(proposalId, 200L, VoteDecision.DISAGREE));
        proposalRepository.flush();

        assertThat(voteRepository.findByProposalId(proposalId)).hasSize(2);
    }

    @Test
    @DisplayName("按状态与发起人过滤列表，返回进行中的提案")
    void shouldPageByStatusAndProposer() {
        ReviewProposal open = proposalRepository.saveAndFlush(openFor(7L));
        ReviewProposal resolved = proposalRepository.saveAndFlush(openFor(8L));
        resolved.transitionTo(new ProposalState.Rejected(PROPOSER_ID, "处置无法执行"));
        proposalRepository.saveAndFlush(resolved);

        List<ReviewProposal> openPage = proposalRepository.findAdminPage(
                ProposalStatus.OPEN, PROPOSER_ID, null, null, PageRequest.of(0, 10));
        assertThat(openPage).extracting(ReviewProposal::getId).containsExactly(open.getId());

        List<ReviewProposal> allPage = proposalRepository.findAdminPage(
                null, null, null, null, PageRequest.of(0, 10));
        assertThat(allPage).hasSize(2);
    }

    @Test
    @DisplayName("按主键加锁读取返回该提案（投票与裁决的前置）")
    void shouldFindForUpdate() {
        Long proposalId = proposalRepository.saveAndFlush(openFor(7L)).getId();

        Optional<ReviewProposal> found = proposalRepository.findForUpdate(proposalId);

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ProposalStatus.OPEN);
    }

    private ReviewProposal openFor(long targetId) {
        return ReviewProposal.open(targetId, ProposalAction.TAKE_DOWN, PROPOSER_ID,
                ReportReason.ABUSE, "人身攻击", 4);
    }
}
