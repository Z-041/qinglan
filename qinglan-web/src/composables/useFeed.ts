import { ref } from 'vue'

import { fetchPosts, likePost, unlikePost } from '@/api/post'
import type { PostSummary } from '@/types/post'

/**
 * 首页信息流：话题筛选 + 游标分页 + 点赞。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），这里只结束 loading 态，
 * view 依据列表是否为空渲染空态，不内联渲染错误文案。
 */
export function useFeed() {
  const posts = ref<PostSummary[]>([])
  const topicId = ref<number | null>(null)
  const loading = ref(false)
  const loadingMore = ref(false)
  const hasMore = ref(false)

  /** 不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let cursor: number | null = null

  function applyPage(items: PostSummary[], nextCursor: number | null, append: boolean): void {
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
    loading.value = true
    try {
      const page = await fetchPosts({ topic: topicId.value })
      applyPage(page.items, page.nextCursor, false)
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
      const page = await fetchPosts({ topic: topicId.value, cursor })
      applyPage(page.items, page.nextCursor, true)
    } catch {
      // 错误已由拦截器提示
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

  return { posts, topicId, loading, loadingMore, hasMore, load, refresh, loadMore, toggleLike }
}
