import { ref } from 'vue'

import { fetchAdminReviewQueue } from '@/api/review'
import type { AdminQueueItem } from '@/types/review'

/**
 * 首报距今超过该小时数即视为超时，行内标红。
 * 后端 qinglan.review 只有 autoTakeDownThreshold 与 appealWindow，没有等待时长阈值，
 * 不为此新增后端配置（spec 5.4），故定为前端常量。
 */
export const REPORT_OVERDUE_HOURS = 24

const MILLIS_PER_HOUR = 3_600_000

/** 首报时间是否已超时。now 可注入，便于测试 */
export function isReportOverdue(firstReportedAt: string, now: number = Date.now()): boolean {
  return now - new Date(firstReportedAt).getTime() > REPORT_OVERDUE_HOURS * MILLIS_PER_HOUR
}

/**
 * 管理端举报队列：游标分页 + 刷新。
 * 排序（举报数降序 + 等待时长升序）完全由后端负责，前端不再排序。
 */
export function useReviewQueue() {
  const items = ref<AdminQueueItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  function applyPage(page: { items: AdminQueueItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 处置成功后重拉第一页（spec 5.5：局部刷新，不整页 reload） */
  async function refresh(): Promise<void> {
    loading.value = true
    try {
      applyPage(await fetchAdminReviewQueue(null), false)
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
      applyPage(await fetchAdminReviewQueue(cursor), true)
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

  return { items, loading, loadingMore, hasMore, refresh, loadMore }
}
