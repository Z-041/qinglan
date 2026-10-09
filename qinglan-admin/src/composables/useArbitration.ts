import { ref } from 'vue'

import { useCursorList } from '@shared/composables/useCursorList'

import { arbitrateProposal, fetchEscalatedProposals } from '@/api/proposals'
import type { ArbitratePayload, EscalatedProposal } from '@/types/proposal'

/**
 * 「待裁决」列表（仅超管）：僵局提案的游标分页（状态机收在 {@link useCursorList}）+ 裁决。
 *
 * 与委员会看到的不同：每行带每一票明细（voterId / 决定 / 时间），这是超管独有的能力（决策 D8）。
 */
export function useArbitration() {
  /** 裁决在途：按钮据此禁用，防重复提交 */
  const submitting = ref(false)

  const list = useCursorList<EscalatedProposal>({
    fetchPage: (cursor) => fetchEscalatedProposals(cursor),
  })

  /** 裁决成功后重拉第一页 */
  async function arbitrate(proposalId: number, payload: ArbitratePayload): Promise<boolean> {
    submitting.value = true
    try {
      await arbitrateProposal(proposalId, payload)
      await list.refresh()
      return true
    } catch {
      // 错误已由拦截器提示
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    submitting,
    refresh: list.refresh,
    loadMore: list.loadMore,
    arbitrate,
  }
}
