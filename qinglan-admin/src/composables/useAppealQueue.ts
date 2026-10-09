import { useCursorList } from '@shared/composables/useCursorList'

import { fetchAdminAppeals } from '@/api/review'
import type { AdminAppealItem } from '@/types/review'

/**
 * 待复审申诉队列：FIFO（后端按 created_at asc 排，先到先审），游标分页（状态机收在 {@link useCursorList}）。
 * 队列里没有申诉人字段——申诉人就是作者，作者身份不出队列（不变量 #1 引申）。
 */
export function useAppealQueue() {
  const list = useCursorList<AdminAppealItem>({
    fetchPage: (cursor) => fetchAdminAppeals(cursor),
  })

  /**
   * 轮询探针：只取首屏的行键用于「是否有新内容」比对。
   * 不写列表、不动游标，故与 `refresh` 并行发请求也不会打架。
   */
  async function headKeys(): Promise<string[]> {
    const page = await fetchAdminAppeals(null)
    return page.items.map((row) => String(row.appealId))
  }

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    refresh: list.refresh,
    loadMore: list.loadMore,
    headKeys,
  }
}
