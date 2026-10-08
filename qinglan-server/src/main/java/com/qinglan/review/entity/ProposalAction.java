package com.qinglan.review.entity;

/**
 * 需要委员会表决的处置动作（P5 决策 D5）。
 *
 * <p>只包含**对已发布内容不利**的处置：下架帖子、移除评论、隐藏联系方式、维持下架。
 * 「恢复可见 / 撤销隐藏 / 驳回举报 / 待审帖子的通过驳回」都不在此列——它们是放行或撤回处罚，
 * 单人即可（否则日常清理会被表决拖死，见方案 5.1 与 D5）。</p>
 *
 * <p>目标类型由动作本身决定（{@link #targetType()}）：把「动作」与「目标」绑在一起，
 * 调用方就无法提出「对评论下架」这种不存在的组合。</p>
 */
public enum ProposalAction {

    /** 下架帖子：Approved → TakenDown */
    TAKE_DOWN(ReviewTargetType.POST),
    /** 维持下架：确认举报成立，结案该帖待处理举报（内容已在下架态，无状态迁移） */
    UPHOLD_TAKEDOWN(ReviewTargetType.POST),
    /** 隐藏联系方式：仅作者本人可见（不变量 #2 的处置侧） */
    HIDE_CONTACT(ReviewTargetType.POST),
    /** 移除评论：评论先发后审，这是唯一的兜底处置手段 */
    REMOVE_COMMENT(ReviewTargetType.COMMENT);

    private final ReviewTargetType targetType;

    ProposalAction(ReviewTargetType targetType) {
        this.targetType = targetType;
    }

    /** 该动作作用于哪类目标；提案创建时据它推导 target_type，避免两者不一致 */
    public ReviewTargetType targetType() {
        return targetType;
    }
}
