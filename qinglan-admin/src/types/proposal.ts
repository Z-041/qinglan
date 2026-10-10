/**
 * 管理端处置提案与表决的类型（P5 二期，设计 7.4）。
 *
 * 与后端 review/dto 的 Proposal* 系列一一对应。提案列表**刻意不含发起人身份**，
 * 也不暴露「谁投了什么」——真名与每一票明细只走超管的待裁决端点（决策 D8）。
 */

import type { ReviewTargetType } from '@shared/types/review-common'

/** 与后端 review/entity/ProposalStatus 一一对应 */
export type ProposalStatus = 'OPEN' | 'APPROVED' | 'REJECTED' | 'ESCALATED'

/** 与后端 review/entity/ProposalAction 一一对应（只含需表决的「不利处置」） */
export type ProposalAction = 'TAKE_DOWN' | 'UPHOLD_TAKEDOWN' | 'HIDE_CONTACT' | 'REMOVE_COMMENT'

/** 与后端 review/entity/VoteDecision 一一对应；没有弃权，不投即弃权 */
export type VoteDecision = 'AGREE' | 'DISAGREE'

/** 与后端 review/entity/ArbitrationDecision 一一对应；仅超管裁决使用 */
export type ArbitrationDecision = 'GRANT' | 'DENY'

/** 与后端 review/dto/ProposalSummaryResponse 一一对应（列表的一行） */
export interface ProposalSummary {
  proposalId: number
  targetType: ReviewTargetType
  targetId: number
  /** 目标内容摘要（后端已截断） */
  preview: string
  action: ProposalAction
  status: ProposalStatus
  agreeCount: number
  disagreeCount: number
  /** 通过所需的同意票数（按提案创建时的在册人数快照算，全程不变） */
  needed: number
  /** 已投票数（同意 + 不同意） */
  votedCount: number
  /** 调用者自己那一票；未投为 null */
  myDecision: VoteDecision | null
  createdAt: string
}

/** 与后端 review/dto/VoteResultResponse 一一对应（投票后的汇总） */
export interface VoteResult {
  status: ProposalStatus
  agreeCount: number
  disagreeCount: number
  needed: number
  myDecision: VoteDecision
}

/** 与后端 review/dto/ProposalVoteItem 一一对应（待裁决里的一票） */
export interface ProposalVoteItem {
  voterId: number
  decision: VoteDecision
  createdAt: string
}

/**
 * 与后端 review/dto/EscalatedProposalResponse 一一对应。
 * 比 ProposalSummary 多了每一票明细，且**没有 myDecision**——超管是来裁决的，不是投票人。
 */
export interface EscalatedProposal extends Omit<ProposalSummary, 'myDecision'> {
  votes: ProposalVoteItem[]
}

/** 与后端 review/dto/VoteRequest 一一对应 */
export interface VotePayload {
  decision: VoteDecision
}

/** 与后端 review/dto/ArbitrateRequest 一一对应；reason 必填 */
export interface ArbitratePayload {
  decision: ArbitrationDecision
  reason: string
}

/** 动作中文名。未知取值退化为通用文案，避免把裸码渲染给用户 */
export const PROPOSAL_ACTION_LABELS: Record<ProposalAction, string> = {
  TAKE_DOWN: '下架帖子',
  UPHOLD_TAKEDOWN: '维持下架',
  HIDE_CONTACT: '隐藏联系方式',
  REMOVE_COMMENT: '移除评论',
}

/** 提案状态中文名 */
export const PROPOSAL_STATUS_LABELS: Record<ProposalStatus, string> = {
  OPEN: '进行中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  ESCALATED: '僵局待裁决',
}

/** 取动作文案；后端版本不同步拿到未知码时退化，不渲染裸码 */
export function proposalActionLabel(action: ProposalAction): string {
  return PROPOSAL_ACTION_LABELS[action] ?? '未知动作'
}

/** 取状态文案，兜底同上 */
export function proposalStatusLabel(status: ProposalStatus): string {
  return PROPOSAL_STATUS_LABELS[status] ?? '未知'
}
