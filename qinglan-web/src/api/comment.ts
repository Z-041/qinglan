import { httpDelete, httpGet, httpPost } from '@shared/api/http'

import type { Comment, CreateCommentRequest } from '@/types/comment'
import type { LikeState } from '@/types/interaction'

/** 评论列表（一级 + 其回复），游客可读 */
export function fetchComments(postId: number): Promise<Comment[]> {
  return httpGet<Comment[]>(`/posts/${postId}/comments`)
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
