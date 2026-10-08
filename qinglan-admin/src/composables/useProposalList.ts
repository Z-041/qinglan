import { ref, watch } from 'vue'

import { fetchAdminProposals, voteProposal } from '@/api/proposals'
import type { ProposalStatus, ProposalSummary, VoteDecision } from '@/types/proposal'

/**
 * 「我的提案与投票」列表：游标分页 + 状态 / 只看我发起的筛选 + 投票。
 *
 * 排序完全由后端负责，前端不再排序。筛选变化即重拉第一页——
 * 否则会出现「切了筛选，列表还是上一个筛选的结果」。
 */
export function useProposalList() {
  const items = ref<ProposalSummary[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏是否失败过。用于把「加载失败」与「暂无提案」分成两屏 */
  const failed = ref(false)
  /** 投票在途：行内按钮据此禁用，防重复投票 */
  const submitting = ref(false)

  /** 顶部筛选：默认看进行中；打开「只看我发起的」即「我的提案」 */
  const status = ref<ProposalStatus>('OPEN')
  const mine = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。投票后的 refresh 与用户点「刷新」/ 切筛选可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新结果覆盖掉，列表与游标随之错位。
   */
  let requestSeq = 0

  function applyPage(page: { items: ProposalSummary[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  async function refresh(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：否则本次失败时列表与游标会留在上一次的结果集上，
    // 此后点「加载更多」就会拿「新一次 + 旧游标」去请求
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await fetchAdminProposals(status.value, mine.value, null)
      // 过期响应必须丢弃，否则旧结果会覆盖新结果
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, false)
    } catch {
      // 错误已由拦截器提示；只有当前这次请求才有权把状态置为失败
      if (seq === requestSeq) {
        failed.value = true
      }
    } finally {
      if (seq === requestSeq) {
        loading.value = false
      }
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    // 记下发起时的序号：期间若刷新过（requestSeq 会 +1），这次的响应必须丢弃
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchAdminProposals(status.value, mine.value, cursor)
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

  /** 投票成功后重拉第一页，保留当前筛选 */
  async function vote(proposalId: number, decision: VoteDecision): Promise<boolean> {
    submitting.value = true
    try {
      await voteProposal(proposalId, { decision })
      await refresh()
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
    void refresh()
  })

  return {
    items,
    loading,
    loadingMore,
    hasMore,
    failed,
    submitting,
    status,
    mine,
    refresh,
    loadMore,
    vote,
  }
}
