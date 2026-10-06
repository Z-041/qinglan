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

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  function applyPage(page: { items: AdminAppealItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 复审成功后重拉第一页，已复审的不再出现（后端只返回 PENDING） */
  async function refresh(): Promise<void> {
    loading.value = true
    try {
      applyPage(await fetchAdminAppeals(null), false)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
    try {
      applyPage(await fetchAdminAppeals(cursor), true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

  return { items, loading, loadingMore, hasMore, refresh, loadMore }
}
