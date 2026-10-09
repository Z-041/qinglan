import { useCursorList } from '@shared/composables/useCursorList'

import { fetchMyPosts } from '@/api/post'
import type { MyPostItem } from '@/types/post'

/**
 * 我的帖子列表：游标分页 + 四态。
 *
 * 分页状态机（清空再请求、请求序号防竞态、loading/loadingMore/hasMore/failed）收在
 * {@link useCursorList}；本 composable 只提供取页函数。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），本 composable **不渲染任何请求错误文案**；
 * 只透出 `failed` 状态位，让 view 把「加载失败」与「真的没有帖子」分成两屏（沿用 useFeed 的惯例）。
 */
export function useMyPosts() {
  const list = useCursorList<MyPostItem>({
    fetchPage: (cursor) => fetchMyPosts({ cursor }),
  })

  return {
    items: list.items,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    load: list.refresh,
    loadMore: list.loadMore,
  }
}
