import { ref } from 'vue'

import { createComment, deleteComment, fetchComments, likeComment, unlikeComment } from '@/api/comment'
import { fetchPostDetail, likePost, unlikePost } from '@/api/post'
import type { Comment } from '@/types/comment'
import type { PostDetail } from '@/types/post'

/**
 * 帖子详情：正文 + 联系方式（服务端已按 viewer 脱敏）+ 二级评论 + 点赞。
 *
 * 请求失败由 http 拦截器统一弹窗，本 composable 只负责成功后的状态推进。
 */
export function usePostDetail(postId: number) {
  const post = ref<PostDetail | null>(null)
  const comments = ref<Comment[]>([])
  const loading = ref(false)
  const submitting = ref(false)

  /** 评论计数由列表实际条数推导，删一级评论会级联删回复，直接复用后端返回值反而容易不一致 */
  function syncCommentCount(): void {
    if (post.value) {
      post.value.commentCount = comments.value.reduce(
        (total, item) => total + 1 + item.replies.length,
        0,
      )
    }
  }

  async function load(): Promise<void> {
    loading.value = true
    try {
      const [detail, list] = await Promise.all([fetchPostDetail(postId), fetchComments(postId)])
      post.value = detail
      comments.value = list
    } catch {
      // 错误已由拦截器提示
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
    submitting.value = true
    try {
      await createComment(postId, { content, parentId })
      // 重新拉取以保证排序与层级与后端一致
      comments.value = await fetchComments(postId)
      syncCommentCount()
      return true
    } catch {
      return false
    } finally {
      submitting.value = false
    }
  }

  /** 删除自己的评论（一级评论会连同其回复一起删除） */
  async function removeComment(commentId: number): Promise<void> {
    try {
      await deleteComment(commentId)
      comments.value = await fetchComments(postId)
      syncCommentCount()
    } catch {
      // 错误已由拦截器提示
    }
  }

  return {
    post,
    comments,
    loading,
    submitting,
    load,
    toggleLike,
    toggleCommentLike,
    submitComment,
    removeComment,
  }
}
