package com.qinglan.review.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提案状态机的测试（P5 方案 5.7）。
 *
 * <p>盯住两条：僵局只能被超管裁决（不能回去继续投票），以及**终态不可再变**——
 * 「表决通过后又被改判」这条路必须堵死，否则决议可以被反复推翻。</p>
 */
class ProposalStateTest {

    @Test
    @DisplayName("进行中可直达三种结局：准予 / 不予 / 上报超管")
    void openCanReachEveryOutcome() {
        assertThat(ProposalState.canTransition(ProposalStatus.OPEN, ProposalStatus.APPROVED)).isTrue();
        assertThat(ProposalState.canTransition(ProposalStatus.OPEN, ProposalStatus.REJECTED)).isTrue();
        assertThat(ProposalState.canTransition(ProposalStatus.OPEN, ProposalStatus.ESCALATED)).isTrue();
    }

    @Test
    @DisplayName("僵局只能由超管裁决收场：可准予也可驳回，但不能回到进行中")
    void escalatedCanOnlyBeResolvedByArbitration() {
        assertThat(ProposalState.canTransition(ProposalStatus.ESCALATED, ProposalStatus.APPROVED)).isTrue();
        assertThat(ProposalState.canTransition(ProposalStatus.ESCALATED, ProposalStatus.REJECTED)).isTrue();
        assertThat(ProposalState.canTransition(ProposalStatus.ESCALATED, ProposalStatus.OPEN)).isFalse();
    }

    @Test
    @DisplayName("终态不可再变：结案后的提案不能重新投票或被改判")
    void finalStatesAreTerminal() {
        for (ProposalStatus terminal : new ProposalStatus[]{ProposalStatus.APPROVED, ProposalStatus.REJECTED}) {
            for (ProposalStatus target : ProposalStatus.values()) {
                assertThat(ProposalState.canTransition(terminal, target))
                        .as("%s → %s 不应合法", terminal, target)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("非法迁移抛 ILLEGAL_REVIEW_TRANSITION 而不是静默改状态")
    void illegalTransitionThrows() {
        assertThatThrownBy(() -> ProposalState.assertTransition(
                ProposalStatus.APPROVED, ProposalStatus.REJECTED))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.ILLEGAL_REVIEW_TRANSITION);
        assertThatThrownBy(() -> ProposalState.assertTransition(
                ProposalStatus.OPEN, ProposalStatus.OPEN))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("各状态记录的 status() 与持久化名一致")
    void recordsMapToPersistedStatus() {
        assertThat(new ProposalState.Open().status()).isEqualTo(ProposalStatus.OPEN);
        assertThat(new ProposalState.Escalated().status()).isEqualTo(ProposalStatus.ESCALATED);
        assertThat(new ProposalState.Approved(1L, "表决通过").status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(new ProposalState.Rejected(1L, "超管驳回").status()).isEqualTo(ProposalStatus.REJECTED);
    }
}
