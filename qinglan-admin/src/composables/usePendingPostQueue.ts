import { useCursorList } from '@shared/composables/useCursorList'

import { fetchAdminPendingPosts } from '@/api/review'
import type { AdminPendingPostItem } from '@/types/review'

/**
 * 待审帖子队列：FIFO（后端按 created_at asc 排，先到先审），游标分页（状态机收在 {@link useCursorList}）。
 *
 * 不复用举报队列的 composable：两者接口与语义都不同——举报队列按「被举报目标」聚合，
 * 本队列列的是「已提交、尚未放行」的帖子（先审后发）。
 */
export function usePendingPostQueue() {
  const list = useCursorList<AdminPendingPostItem>({
    fetchPage: (cursor) => fetchAdminPendingPosts(cursor),
  })

  /**
   * 轮询探针：只取首屏的行键用于「是否有新内容」比对。
   * 不写列表、不动游标，故与 `refresh` 并行发请求也不会打架。
   */
  async function headKeys(): Promise<string[]> {
    const page = await fetchAdminPendingPosts(null)
    return page.items.map((row) => String(row.postId))
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
