import { httpDelete, httpGet, httpPost } from './http'

import type { CursorPage } from '@/types/api'
import type { LikeState } from '@/types/interaction'
import type { CreatePostRequest, PostDetail, PostSummary } from '@/types/post'

/** 信息流查询条件；topic 为空表示全部话题，cursor 为空表示第一页 */
export interface FetchPostsParams {
  topic?: number | null
  cursor?: string | null
}

function buildFeedQuery(params: FetchPostsParams): string {
  const search = new URLSearchParams()
  if (params.topic != null) {
    search.set('topic', String(params.topic))
  }
  if (params.cursor != null) {
    search.set('cursor', String(params.cursor))
  }
  const query = search.toString()
  return query ? `/posts?${query}` : '/posts'
}

/** 发布帖子，落 Pending 待审（红线 2：主帖先审后发） */
export function createPost(payload: CreatePostRequest): Promise<PostDetail> {
  return httpPost<PostDetail>('/posts', payload)
}

export function fetchPosts(params: FetchPostsParams = {}): Promise<CursorPage<PostSummary>> {
  return httpGet<CursorPage<PostSummary>>(buildFeedQuery(params))
}

export function fetchPostDetail(postId: number): Promise<PostDetail> {
  return httpGet<PostDetail>(`/posts/${postId}`)
}

export function likePost(postId: number): Promise<LikeState> {
  return httpPost<LikeState>(`/posts/${postId}/like`)
}

export function unlikePost(postId: number): Promise<LikeState> {
  return httpDelete<LikeState>(`/posts/${postId}/like`)
}
