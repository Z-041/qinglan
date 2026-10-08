import { ref } from 'vue'

import { arbitrateProposal, fetchEscalatedProposals } from '@/api/proposals'
import type { ArbitratePayload, EscalatedProposal } from '@/types/proposal'

/**
 * 「待裁决」列表（仅超管）：僵局提案的游标分页 + 裁决。
 *
 * 与委员会看到的不同：每行带每一票明细（voterId / 决定 / 时间），这是超管独有的能力（决策 D8）。
 */
export function useArbitration() {
  const items = ref<EscalatedProposal[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏是否失败过。用于把「加载失败」与「暂无待裁决」分成两屏 */
  const failed = ref(false)
  /** 裁决在途：按钮据此禁用，防重复提交 */
  const submitting = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /** 请求序号。裁决后的 refresh 与用户点「刷新」可能同时在飞，先发的不一定先到 */
  let requestSeq = 0

  function applyPage(page: { items: EscalatedProposal[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  async function refresh(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求，避免失败时列表与游标留在上一次的结果集上
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await fetchEscalatedProposals(null)
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
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchEscalatedProposals(cursor)
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

  /** 裁决成功后重拉第一页 */
  async function arbitrate(proposalId: number, payload: ArbitratePayload): Promise<boolean> {
    submitting.value = true
    try {
      await arbitrateProposal(proposalId, payload)
      await refresh()
      return true
    } catch {
      // 错误已由拦截器提示
      return false
    } finally {
      submitting.value = false
    }
  }

  return { items, loading, loadingMore, hasMore, failed, submitting, refresh, loadMore, arbitrate }
}
