import { ref } from 'vue'

import { fetchAdminPendingPosts } from '@/api/review'
import type { AdminPendingPostItem } from '@/types/review'

/**
 * 待审帖子队列：FIFO（后端按 created_at asc 排，先到先审），游标分页。
 *
 * 不复用举报队列的 composable：两者接口与语义都不同——举报队列按「被举报目标」聚合，
 * 本队列列的是「已提交、尚未放行」的帖子（先审后发）。
 */
export function usePendingPostQueue() {
  const items = ref<AdminPendingPostItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏是否失败过。用于把「加载失败」与「暂无待审帖子」分成两屏 */
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。处置成功后 refresh 与用户点「刷新」可能同时在飞，**先发的不一定先到**：
   * 没有它的话，晚到的旧响应会把新结果覆盖掉。
   */
  let requestSeq = 0

  function applyPage(page: { items: AdminPendingPostItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 处置成功后重拉第一页，已放行的不再出现（后端只返回待审状态） */
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
      applyPage(await fetchAdminPendingPosts(null), false)
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
      const page = await fetchAdminPendingPosts(cursor)
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

  return { items, loading, loadingMore, hasMore, failed, refresh, loadMore }
}
