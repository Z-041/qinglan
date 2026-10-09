import { ref } from 'vue'

import { createComment, deleteComment, fetchComments, likeComment, unlikeComment } from '@/api/comment'
import { fetchPostDetail, likePost, unlikePost } from '@/api/post'
import { fetchMyReportState } from '@/api/report'
import { submitAppeal as requestAppeal } from '@shared/api/report'
import { useSubmit } from '@shared/composables/useSubmit'
import { useUserStore } from '@shared/stores/user'
import type { Comment } from '@/types/comment'
import type { PostDetail } from '@/types/post'
import type { MyReportState } from '@/types/report'

/**
 * 帖子详情：正文 + 联系方式（服务端已按 viewer 脱敏）+ 二级评论 + 点赞 + 作者申诉。
 *
 * 请求失败由 http 拦截器统一弹窗，本 composable 只负责成功后的状态推进。
 */
export function usePostDetail(postId: number) {
  const userStore = useUserStore()
  const post = ref<PostDetail | null>(null)
  const comments = ref<Comment[]>([])
  /**
   * 当前用户在本帖上的举报状态。未登录或未举报时为 null。
   * 它只是**体验层**（禁用重复举报、展示处理进度）；真正的防线仍是后端不变量 #4（重复举报 409）。
   */
  const reportState = ref<MyReportState | null>(null)
  const loading = ref(false)
  const loadingMore = ref(false)
  /** 是否还有下一页一级评论；由后端 nextCursor 推导，末页为 false */
  const hasMore = ref(false)
  const { submitting, run: runComment } = useSubmit()
  const { submitting: appealSubmitting, run: runAppeal } = useSubmit()
  /** 正文/评论加载失败过。用于把「失败」渲染成带重试的屏，而不是一片空白 */
  const failed = ref(false)

  /** 一级评论下一页游标。不放进 ref：只作为下一次请求的入参，变化不需要触发渲染 */
  let commentCursor: string | null = null
  /**
   * 本次会话内是否已提交过申诉。
   *
   * 后端「每帖限一次」由 `uk_appeal_target` 兜底，但申诉状态属于 review 模块，
   * post 模块不得反向依赖它，故详情响应里没有该字段——前端只记住本次结果，
   * 刷新后重新点会拿到 409 提示，与实际一致。
   */
  const appealed = ref(false)

  /**
   * 拉取一级评论首页并重置分页游标。写操作（发表 / 删除）后重拉首页，保证排序与层级与后端一致；
   * 与后端一致，一级评论按「最早在前」排序，新发的评论落在末页。
   */
  async function loadCommentFirstPage(): Promise<void> {
    const page = await fetchComments(postId, null)
    comments.value = page.items
    commentCursor = page.nextCursor
    hasMore.value = page.nextCursor !== null
  }

  /**
   * 加载下一批一级评论（回复随所属一级评论整页带出）。
   * 无下一页、或首屏/上一批仍在加载时直接返回，避免重复请求。
   */
  async function loadMoreComments(): Promise<void> {
    if (!hasMore.value || loading.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
    try {
      const page = await fetchComments(postId, commentCursor)
      comments.value = [...comments.value, ...page.items]
      commentCursor = page.nextCursor
      hasMore.value = page.nextCursor !== null
    } catch {
      // 错误已由拦截器提示
    } finally {
      loadingMore.value = false
    }
  }

  /** 在详情返回的权威计数上做增量：删一级评论会级联删回复，用本地已知的回复条数修正 */
  function adjustCommentCount(delta: number): void {
    if (post.value) {
      post.value.commentCount += delta
    }
  }

  /**
   * 拉取当前用户在本帖上的举报状态；未登录时清空且不发请求（该接口需登录，游客会拿到 401）。
   *
   * 这是附加信息：失败不影响详情主流程，故自行吞掉错误（具体提示仍由 http 拦截器给出），
   * 只是把状态置空、举报入口维持可点，让用户仍能正常举报。
   */
  async function loadReportState(): Promise<void> {
    if (!userStore.isAuthenticated) {
      reportState.value = null
      return
    }
    try {
      reportState.value = await fetchMyReportState('POST', postId)
    } catch {
      reportState.value = null
    }
  }

  async function load(): Promise<void> {
    loading.value = true
    failed.value = false
    try {
      const [detail, page] = await Promise.all([fetchPostDetail(postId), fetchComments(postId, null)])
      post.value = detail
      comments.value = page.items
      commentCursor = page.nextCursor
      hasMore.value = page.nextCursor !== null
      await loadReportState()
    } catch {
      // 错误已由拦截器提示；这里只置状态位，让 view 给出「重试」出口而不是留一片空白
      failed.value = true
    } finally {
      loading.value = false
    }
  }

  async function toggleLike(): Promise<void> {
    if (!post.value) {
      return
    }
    try {
      const state = post.value.liked ? await unlikePost(postId) : await likePost(postId)
      post.value.liked = state.liked
      post.value.likeCount = state.likeCount
    } catch {
      // 错误已由拦截器提示
    }
  }

  function findComment(commentId: number): Comment | undefined {
    for (const item of comments.value) {
      if (item.id === commentId) {
        return item
      }
      const reply = item.replies.find((candidate) => candidate.id === commentId)
      if (reply) {
        return reply
      }
    }
    return undefined
  }

  async function toggleCommentLike(commentId: number): Promise<void> {
    const target = findComment(commentId)
    if (!target) {
      return
    }
    try {
      const state = target.liked ? await unlikeComment(commentId) : await likeComment(commentId)
      target.liked = state.liked
      target.likeCount = state.likeCount
    } catch {
      // 错误已由拦截器提示
    }
  }

  /** 发表评论或回复；返回是否成功，失败时由调用方保留已输入的草稿 */
  async function submitComment(content: string, parentId: number | null = null): Promise<boolean> {
    return runComment(async () => {
      await createComment(postId, { content, parentId })
      // 重新拉取首页以保证排序与层级与后端一致（一级评论最早在前，新评论落在末页）
      await loadCommentFirstPage()
      adjustCommentCount(1)
    })
  }

  /** 删除自己的评论（一级评论会连同其回复一起删除） */
  async function removeComment(commentId: number): Promise<void> {
    const target = findComment(commentId)
    try {
      await deleteComment(commentId)
      // 一级评论会级联删除其回复；回复随所属一级评论整页返回，本地已知其条数
      adjustCommentCount(-(target ? 1 + target.replies.length : 1))
      await loadCommentFirstPage()
    } catch {
      // 错误已由拦截器提示
    }
  }

  /** 作者对已下架帖子申诉；返回是否成功，失败时由调用方保留已输入的理由 */
  async function submitAppeal(reason: string): Promise<boolean> {
    return runAppeal(async () => {
      await requestAppeal({ postId, reason })
      appealed.value = true
    })
  }

  return {
    post,
    comments,
    reportState,
    loading,
    loadingMore,
    hasMore,
    failed,
    submitting,
    appealSubmitting,
    appealed,
    load,
    refreshReportState: loadReportState,
    loadMoreComments,
    toggleLike,
    toggleCommentLike,
    submitComment,
    removeComment,
    submitAppeal,
  }
}
