import { ref } from 'vue'

import { fetchPosts, likePost, unlikePost } from '@/api/post'
import type { PostSummary } from '@/types/post'

/**
 * 首页信息流：话题筛选 + 游标分页 + 点赞。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），本 composable **不渲染任何请求错误文案**；
 * 它只额外给出 `failed` 这个**状态位**，让 view 能把「加载失败」与「真的没有内容」分成两屏
 * （前者给一个「重试」按钮，后者才是引导发帖的空态）。
 */
export function useFeed() {
  const posts = ref<PostSummary[]>([])
  const topicId = ref<number | null>(null)
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)
  /** 首屏/切换话题的请求是否失败过。用于把「加载失败」与「真的没有内容」区分开 */
  const failed = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: string | null = null

  /**
   * 请求序号。切换话题会有多个请求同时在飞，**后发的不一定后到**：
   * 没有它的话，先点 A 再点 B、而 A 的响应晚到时，列表会写成 A 的内容、chip 却高亮 B。
   */
  let requestSeq = 0

  function applyPage(items: PostSummary[], nextCursor: string | null, append: boolean): void {
    posts.value = append ? [...posts.value, ...items] : items
    cursor = nextCursor
    hasMore.value = nextCursor !== null
  }

  /** 切换话题并重置列表与游标 */
  async function load(targetTopicId: number | null): Promise<void> {
    topicId.value = targetTopicId
    await refresh()
  }

  /** 按当前话题重新拉第一页；供下拉刷新与话题变更后调用 */
  async function refresh(): Promise<void> {
    const seq = ++requestSeq
    loading.value = true
    failed.value = false
    // 先清干净再请求：否则本次失败时列表与游标会留在**上一个话题**上，
    // 随后点「加载更多」就会发「新话题 + 旧话题游标」这种自相矛盾的请求
    posts.value = []
    cursor = null
    hasMore.value = false
    try {
      const page = await fetchPosts({ topic: topicId.value })
      if (seq !== requestSeq) {
        return
      }
      applyPage(page.items, page.nextCursor, false)
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
    // 记下发起时的序号：期间若切了话题（refresh 会 +1），这次的响应必须丢弃，
    // 否则旧话题的条目会被 append 进新话题的列表
    const seq = requestSeq
    loadingMore.value = true
    try {
      const page = await fetchPosts({ topic: topicId.value, cursor })
      if (seq !== requestSeq) {
        return
      }
      applyPage(page.items, page.nextCursor, true)
    } catch {
      // 错误已由拦截器提示；加载更多失败不清空已加载内容
    } finally {
      loadingMore.value = false
    }
  }

  /**
   * 点赞 / 取消点赞。不做乐观更新——失败时无需回滚，状态始终与后端一致。
   */
  async function toggleLike(post: PostSummary): Promise<void> {
    try {
      const state = post.liked ? await unlikePost(post.id) : await likePost(post.id)
      post.liked = state.liked
      post.likeCount = state.likeCount
    } catch {
      // 错误已由拦截器提示
    }
  }

  return {
    posts,
    topicId,
    loading,
    loadingMore,
    hasMore,
    failed,
    load,
    refresh,
    loadMore,
    toggleLike,
  }
}
