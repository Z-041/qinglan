import { ref, watch } from 'vue'

import { useCursorList } from '@shared/composables/useCursorList'

import { fetchAdminProposals, voteProposal } from '@/api/proposals'
import type { ProposalStatus, ProposalSummary, VoteDecision } from '@/types/proposal'

/**
 * 「我的提案与投票」列表：游标分页（状态机收在 {@link useCursorList}）+ 状态 / 只看我发起的筛选 + 投票。
 *
 * 排序完全由后端负责，前端不再排序。筛选变化即重拉第一页——
 * 否则会出现「切了筛选，列表还是上一个筛选的结果」。取页函数在闭包里读当前筛选。
 */
export function useProposalList() {
  /** 投票在途：行内按钮据此禁用，防重复投票 */
  const submitting = ref(false)

  /** 顶部筛选：默认看进行中；打开「只看我发起的」即「我的提案」 */
  const status = ref<ProposalStatus>('OPEN')
  const mine = ref(false)

  const list = useCursorList<ProposalSummary>({
    fetchPage: (cursor) => fetchAdminProposals(status.value, mine.value, cursor),
  })

  /** 投票成功后重拉第一页，保留当前筛选 */
  async function vote(proposalId: number, decision: VoteDecision): Promise<boolean> {
    submitting.value = true
    try {
      await voteProposal(proposalId, { decision })
      await list.refresh()
      return true
    } catch {
      // 错误已由拦截器提示
      return false
    } finally {
      submitting.value = false
    }
  }

  // 筛选变化即重拉第一页；游标随 refresh 一起清空
  watch([status, mine], () => {
    void list.refresh()
  })

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    submitting,
    status,
    mine,
    refresh: list.refresh,
    loadMore: list.loadMore,
    vote,
  }
}
