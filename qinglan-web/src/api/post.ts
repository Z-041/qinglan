import { httpDelete, httpGet, httpPost } from '@shared/api/http'

import type { CursorPage } from '@shared/types/api'
import type { LikeState } from '@/types/interaction'
import type {
  CreatePostRequest,
  MyPostItem,
  PostDetail,
  PostSort,
  PostStatus,
  PostSummary,
} from '@/types/post'

/**
 * 信息流查询条件；topic 为空表示全部话题，cursor 为空表示第一页。
 *
 * `sort` 缺省即 `latest`（与后端 `PostSort.fromRequest(null)` 一致）；`hot` 时游标格式不同，
 * 服务端据此解析，故**切换排序必须把 sort 一并回传**（见 useFeed）。
 */
export interface FetchPostsParams {
  topic?: number | null
  sort?: PostSort | null
  cursor?: string | null
}

function buildFeedQuery(params: FetchPostsParams): string {
  const search = new URLSearchParams()
  if (params.topic != null) {
    search.set('topic', String(params.topic))
  }
  if (params.sort != null) {
    search.set('sort', params.sort)
  }
  if (params.cursor != null) {
    search.set('cursor', String(params.cursor))
  }
  const query = search.toString()
  return query ? `/posts?${query}` : '/posts'
}

/** 我的帖子查询条件；status 为空表示全部状态，cursor 为空表示第一页 */
export interface FetchMyPostsParams {
  status?: PostStatus | null
  cursor?: string | null
}

function buildMyPostsQuery(params: FetchMyPostsParams): string {
  const search = new URLSearchParams()
  if (params.status != null) {
    search.set('status', params.status)
  }
  if (params.cursor != null) {
    search.set('cursor', String(params.cursor))
  }
  const query = search.toString()
  return query ? `/posts/mine?${query}` : '/posts/mine'
}

/** 发布帖子，落 Pending 待审（红线 2：主帖先审后发） */
export function createPost(payload: CreatePostRequest): Promise<PostDetail> {
  return httpPost<PostDetail>('/posts', payload)
}

export function fetchPosts(params: FetchPostsParams = {}): Promise<CursorPage<PostSummary>> {
  return httpGet<CursorPage<PostSummary>>(buildFeedQuery(params))
}

/**
 * 我的帖子（仅本人）：覆盖待审 / 已驳回 / 已下架 / 已通过全部状态，游标分页。
 *
 * 需登录；出参含本人的审核状态与 `reviewReason`，不含他人身份。
 */
export function fetchMyPosts(params: FetchMyPostsParams = {}): Promise<CursorPage<MyPostItem>> {
  return httpGet<CursorPage<MyPostItem>>(buildMyPostsQuery(params))
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
