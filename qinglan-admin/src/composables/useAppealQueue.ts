import { ref } from 'vue'

import { fetchAdminAppeals } from '@/api/review'
import type { AdminAppealItem } from '@/types/review'

/**
 * 待复审申诉队列：FIFO（后端按 created_at asc 排，先到先审），游标分页。
 * 队列里没有申诉人字段——申诉人就是作者，作者身份不出队列（不变量 #1 引申）。
 */
export function useAppealQueue() {
  const items = ref<AdminAppealItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏是否失败过。用于把「加载失败」与「暂无待复审申诉」分成两屏 */
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。复审成功后 refresh 与用户点「刷新」可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新结果覆盖掉。
   */
  let requestSeq = 0

  function applyPage(page: { items: AdminAppealItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 复审成功后重拉第一页，已复审的不再出现（后端只返回 PENDING） */
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
      const page = await fetchAdminAppeals(null)
      // 过期响应必须丢弃：复审后的 refresh 与用户点「刷新」可能同时在飞，先发的不一定先到
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
      const page = await fetchAdminAppeals(cursor)
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

  /**
   * 轮询探针：只取首屏的行键用于「是否有新内容」比对。
   * 不写列表、不动游标，故与 `refresh` 并行发请求也不会打架。
   */
  async function headKeys(): Promise<string[]> {
    const page = await fetchAdminAppeals(null)
    return page.items.map((row) => String(row.appealId))
  }

  return { items, loading, loadingMore, hasMore, failed, refresh, loadMore, headKeys }
}
