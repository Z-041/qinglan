package com.qinglan.review.entity;

/**
 * 表决计票与阈值判定（P5 方案 5.2 / 5.3）。
 *
 * <p>阈值：**严格过半**，{@code needed = 在册人数 / 2 + 1}（整数除法）。6 人时 4 票、5 人时 3 票。
 * 分母是提案**创建时**在册委员会人数的快照（{@link ReviewProposal#getSnapshotSize()}）——
 * 若按当前人数实时计算，委员一增减，同一条提案的通过线就会漂移。</p>
 *
 * <p>未投票者计入分母：不投等于默认站在反对一侧，避免「拉人弃权」变相放行。</p>
 *
 * @param snapshotSize 提案创建时的在册委员会人数（分母，全程不变）
 * @param agreeCount   同意票数
 * @param disagreeCount 不同意票数
 */
public record VoteTally(int snapshotSize, int agreeCount, int disagreeCount) {

    public VoteTally {
        if (snapshotSize < 1) {
            throw new IllegalArgumentException("在册人数至少为 1，实际 " + snapshotSize);
        }
        if (agreeCount < 0 || disagreeCount < 0 || agreeCount + disagreeCount > snapshotSize) {
            throw new IllegalArgumentException("票数不合法：同意 %d、不同意 %d、在册 %d"
                    .formatted(agreeCount, disagreeCount, snapshotSize));
        }
    }

    /**
     * 由「快照 + 票数」构造，处理票数可能超过快照的唯一途径：**提案创建后委员会新增了成员并投了票**
     * （委员被收回权限时票仍在、快照不变，票数只减不增）。
     *
     * <p>此时把分母抬到实际票数——只会抬高通过线，方向安全；不这么做会撞上构造器的校验直接 500。
     * 之所以不能再严一点（例如拒绝新成员投票），是因为系统没有记录「某人是何时进入委员会的」，
     * 做不到按加入时间判定资格。</p>
     */
    public static VoteTally of(int snapshotSize, int agreeCount, int disagreeCount) {
        return new VoteTally(Math.max(snapshotSize, agreeCount + disagreeCount), agreeCount, disagreeCount);
    }

    /** 已投票数（同意 + 不同意） */
    public int votedCount() {
        return agreeCount + disagreeCount;
    }

    /** 通过所需同意票数：严格过半 */
    public int neededCount() {
        return snapshotSize / 2 + 1;
    }

    /** 是否已达到阈值（可以执行处置） */
    public boolean reached() {
        return agreeCount >= neededCount();
    }

    /**
     * 是否已不可能达到阈值：即使剩下的人全部投同意也不够。
     *
     * <p>一旦成立就立即上报超管，不空等（P5 D7）——继续挂着只是让队列里多一条没人能推动的行。</p>
     */
    public boolean impossible() {
        return agreeCount + (snapshotSize - votedCount()) < neededCount();
    }

    /** 按当前计票应处的状态：达标 → APPROVED、不可能 → ESCALATED、否则仍 OPEN */
    public ProposalStatus decide() {
        if (reached()) {
            return ProposalStatus.APPROVED;
        }
        return impossible() ? ProposalStatus.ESCALATED : ProposalStatus.OPEN;
    }
}
