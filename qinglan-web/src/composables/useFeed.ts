import { ref } from 'vue'

import { useCursorList } from '@shared/composables/useCursorList'

import { fetchPosts, likePost, unlikePost } from '@/api/post'
import type { PostSort, PostSummary } from '@/types/post'

/**
 * 首页信息流：话题筛选 + 最新/热门排序 + 游标分页 + 点赞。
 *
 * 分页状态机（清空再请求、请求序号防竞态、四态）收在 {@link useCursorList}；取页函数在闭包里
 * 读当前话题与排序，因此切话题/切排序只需改 ref 再 `refresh`，无需重建列表。
 *
 * 请求失败一律由 http 拦截器弹窗（AGENTS.md 第七章），本 composable **不渲染任何请求错误文案**；
 * 它只额外给出 `failed` 这个**状态位**，让 view 能把「加载失败」与「真的没有内容」分成两屏
 * （前者给一个「重试」按钮，后者才是引导发帖的空态）。
 *
 * **已知局限（热门排序的游标漂移）**：热门分数由后端每次请求现算，翻页期间若有新点赞/评论，
 * 某条帖子可能位移到游标另一侧，表现为翻页时**偶尔漏掉或重复某条**。这是后端取舍①的固有代价，
 * 前端**不加任何 hack**（例如按 id 去重会把这现象掩盖掉）：游标原样沿用后端返回值，
 * 重复行照常渲染。PostCard 本身无局部状态，重复的 id 不会引发崩溃或闪烁。
 */
export function useFeed() {
  const topicId = ref<number | null>(null)
  /** 排序口径，默认最新。与话题**正交**：切 Tab 不丢话题，切话题不丢 Tab */
  const sort = ref<PostSort>('latest')

  const list = useCursorList<PostSummary>({
    fetchPage: (cursor) => fetchPosts({ topic: topicId.value, sort: sort.value, cursor }),
  })

  /** 切换话题并重置列表与游标 */
  async function load(targetTopicId: number | null): Promise<void> {
    topicId.value = targetTopicId
    await list.refresh()
  }

  /**
   * 切换排序 Tab：重置到第一页，**保留当前话题**（话题与排序正交）。
   * 与话题变更一样走 {@link list.refresh}，因此共用同一套请求序号防竞态。
   */
  async function setSort(next: PostSort): Promise<void> {
    if (next === sort.value) {
      return
    }
    sort.value = next
    await list.refresh()
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
    posts: list.items,
    topicId,
    sort,
    loading: list.loading,
    loadingMore: list.loadingMore,
    hasMore: list.hasMore,
    failed: list.failed,
    load,
    setSort,
    refresh: list.refresh,
    loadMore: list.loadMore,
    toggleLike,
  }
}
