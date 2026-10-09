import { ref } from 'vue'

import { fetchMyPosts } from '@/api/post'
import type { MyPostItem } from '@/types/post'

/**
 * 我的帖子列表：游标分页 + 四态。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），本 composable **不渲染任何请求错误文案**；
 * 它只给出 `failed` 状态位，让 view 把「加载失败」与「真的没有帖子」分成两屏（沿用 useFeed 的惯例）。
 */
export function useMyPosts() {
  const items = ref<MyPostItem[]>([])
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /** 请求序号。重新进入 / 重试可能并发，**后发的不一定后到**，用它丢弃过期响应 */
  let requestSeq = 0

  function applyPage(page: { items: MyPostItem[]; nextCursor: string | null }, append: boolean): void {
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /** 拉第一页并重置游标 */
  async function load(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：否则失败时列表与游标会留在上一次结果上，随后「加载更多」会拼出自相矛盾的请求
    items.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await fetchMyPosts({})
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
    // 记下发起时的序号：期间若重新加载过（load 会 +1），这次的响应必须丢弃
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchMyPosts({ cursor })
      if (seq !== requestSeq) {
        return
      }
      applyPage(page, true)
    } catch {
      // 错误已由拦截器提示；加载更多失败不清空已加载内容
    } finally {
      loadingMore.value = false
    }
  }

  return { items, loading, loadingMore, hasMore, failed, load, loadMore }
}
