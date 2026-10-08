package com.qinglan.review.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 表决阈值的单元测试（P5 方案 5.2 / 5.3）。
 *
 * <p>这块是整期最容易算错的地方：分母是**快照**、要求**严格过半**、未投票计入分母。
 * 三条规则各错一处，结果就是「该过的没过」或「不该过的过了」，且都在界面上看不出来。</p>
 */
class VoteTallyTest {

    @ParameterizedTest(name = "在册 {0} 人时需要 {1} 票同意")
    @CsvSource({"1,1", "2,2", "3,2", "4,3", "5,3", "6,4"})
    @DisplayName("通过线 = 在册人数 / 2 + 1（严格过半）")
    void neededIsStrictMajority(int snapshot, int expected) {
        assertThat(new VoteTally(snapshot, 0, 0).neededCount()).isEqualTo(expected);
    }

    @Test
    @DisplayName("恰好达标即通过：6 人在册时第 4 票同意当场结案")
    void exactlyReachedAtThreshold() {
        VoteTally tally = new VoteTally(6, 4, 2);

        assertThat(tally.reached()).isTrue();
        assertThat(tally.impossible()).isFalse();
        assertThat(tally.decide()).isEqualTo(ProposalStatus.APPROVED);
    }

    @Test
    @DisplayName("of()：票数未超快照时与直接构造等价，分母就是快照")
    void ofKeepsSnapshotWhenVotesWithinIt() {
        VoteTally tally = VoteTally.of(6, 3, 2);

        assertThat(tally.neededCount()).isEqualTo(4);
        assertThat(tally.votedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("of()：提案创建后委员会新增成员并投票，票数超过快照时把分母抬到实际票数（否则直接 500）")
    void ofRaisesDenominatorWhenVotesExceedSnapshot() {
        // 创建时 3 人（needed=2），期间新增 2 名委员并都投了票 → 5 票，超过快照
        VoteTally tally = VoteTally.of(3, 3, 2);

        assertThat(tally.votedCount()).isEqualTo(5);
        // 分母抬到 5 → 通过线变成 3：只会更难通过，不会把「不够票」误判成通过
        assertThat(tally.neededCount()).isEqualTo(3);
        assertThat(tally.decide()).isEqualTo(ProposalStatus.APPROVED);
    }

    @Test
    @DisplayName("差一票仍是进行中：6 人在册 3 票同意、其余未投，理论上还有机会")
    void stillOpenWhenMoreVotesCanReach() {
        VoteTally tally = new VoteTally(6, 3, 0);

        assertThat(tally.reached()).isFalse();
        assertThat(tally.impossible()).isFalse();
        assertThat(tally.decide()).isEqualTo(ProposalStatus.OPEN);
    }

    @Test
    @DisplayName("平票即僵局：6 人在册 3 同意 3 反对，剩下 0 人不可能补到 4 票")
    void tieEscalatesImmediately() {
        VoteTally tally = new VoteTally(6, 3, 3);

        assertThat(tally.reached()).isFalse();
        assertThat(tally.impossible()).isTrue();
        assertThat(tally.decide()).isEqualTo(ProposalStatus.ESCALATED);
    }

    @Test
    @DisplayName("三票反对即僵局：不必等所有人投完（6 人在册最多只能凑到 3 票同意）")
    void threeDisagreementsEscalate() {
        VoteTally tally = new VoteTally(6, 0, 3);

        assertThat(tally.impossible()).isTrue();
        assertThat(tally.decide()).isEqualTo(ProposalStatus.ESCALATED);
    }

    @Test
    @DisplayName("未投票者计入分母：同样 5 人已投，2 同意 3 反对僵局、3 同意 2 反对仍可等")
    void absentVotersCountTowardDenominator() {
        // 6 人在册、只有 1 人投了反对：仍可能过（还有 5 票空间）
        assertThat(new VoteTally(6, 0, 1).impossible()).isFalse();
        // 6 人在册、2 同意 3 反对：只剩 1 人未投，最多凑到 3 票 < 4，不可能过
        assertThat(new VoteTally(6, 2, 3).reached()).isFalse();
        assertThat(new VoteTally(6, 2, 3).impossible()).isTrue();
        // 同样是 5 人投完，但 3 同意 2 反对时还有一人未投，仍可能补到 4 票 → 继续等
        assertThat(new VoteTally(6, 3, 2).impossible()).isFalse();
    }

    @Test
    @DisplayName("单人委员会：他自己投同意即通过，投反对即僵局")
    void singleMemberCommittee() {
        assertThat(new VoteTally(1, 1, 0).decide()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(new VoteTally(1, 0, 1).decide()).isEqualTo(ProposalStatus.ESCALATED);
        assertThat(new VoteTally(1, 0, 0).decide()).isEqualTo(ProposalStatus.OPEN);
    }

    @Test
    @DisplayName("全员同意必然通过，全员反对必然僵局")
    void unanimousCases() {
        assertThat(new VoteTally(6, 6, 0).decide()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(new VoteTally(6, 0, 6).decide()).isEqualTo(ProposalStatus.ESCALATED);
    }

    @Test
    @DisplayName("票数超过在册人数属于非法构造：宁可抛错，也不要算出一个看起来正常的结论")
    void rejectsImpossibleTally() {
        assertThatThrownBy(() -> new VoteTally(3, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoteTally(0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoteTally(3, -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
