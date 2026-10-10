import { httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
import type {
  ArbitratePayload,
  EscalatedProposal,
  ProposalStatus,
  ProposalSummary,
  VotePayload,
  VoteResult,
} from '@/types/proposal'

// P5 二期处置提案与表决（后端 AdminProposalController，前缀 /admin 由 http 封装补全）。

/**
 * 提案列表（游标分页）。
 *
 * @param status 默认 OPEN（进行中）；可传 ESCALATED 看僵局待裁决
 * @param mine   只看自己发起的（「我的提案」视图）
 */
export function fetchAdminProposals(
  status: ProposalStatus | null,
  mine: boolean,
  cursor: string | null,
): Promise<CursorPage<ProposalSummary>> {
  return httpGet<CursorPage<ProposalSummary>>('/admin/proposals', { status, mine, cursor })
}

/** 投票；达阈值即在同一事务内执行处置，不可能过半即转超管裁决 */
export function voteProposal(proposalId: number, payload: VotePayload): Promise<VoteResult> {
  return httpPost<VoteResult>(`/admin/proposals/${proposalId}/vote`, payload)
}

/** 待裁决列表（仅超管）：出参带每一票明细 */
export function fetchEscalatedProposals(cursor: string | null): Promise<CursorPage<EscalatedProposal>> {
  return httpGet<CursorPage<EscalatedProposal>>('/admin/proposals/escalated', { cursor })
}

/** 超管裁决：准予执行处置或驳回；必须填理由，全程留痕 */
export function arbitrateProposal(proposalId: number, payload: ArbitratePayload): Promise<void> {
  return httpPost<void>(`/admin/proposals/${proposalId}/arbitrate`, payload)
}
