import { httpDelete, httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
import type { Comment, CreateCommentRequest } from '@/types/comment'
import type { LikeState } from '@/types/interaction'

/**
 * 评论列表（一级评论游标分页，每页带出这些一级评论的全部回复），游客可读。
 * cursor 为 null 表示第一页；游标对客户端不透明，原样回传即可。
 */
export function fetchComments(postId: number, cursor: string | null = null): Promise<CursorPage<Comment>> {
  const query = cursor != null ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return httpGet<CursorPage<Comment>>(`/posts/${postId}/comments${query}`)
}

/** parentId 为空即发一级评论；否则回复该帖下的一级评论 */
export function createComment(
  postId: number,
  payload: CreateCommentRequest,
): Promise<Comment> {
  return httpPost<Comment>(`/posts/${postId}/comments`, payload)
}

export function deleteComment(commentId: number): Promise<void> {
  return httpDelete<void>(`/comments/${commentId}`)
}

export function likeComment(commentId: number): Promise<LikeState> {
  return httpPost<LikeState>(`/comments/${commentId}/like`)
}

export function unlikeComment(commentId: number): Promise<LikeState> {
  return httpDelete<LikeState>(`/comments/${commentId}/like`)
}
