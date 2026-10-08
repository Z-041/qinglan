import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createComment, deleteComment, fetchComments, likeComment, unlikeComment } from '@/api/comment'
import { fetchPostDetail, likePost, unlikePost } from '@/api/post'
import { submitAppeal } from '@shared/api/report'
import { usePostDetail } from '@/composables/usePostDetail'
import type { CursorPage } from '@shared/types/api'
import type { Comment } from '@/types/comment'
import type { PostDetail } from '@/types/post'

vi.mock('@/api/comment', () => ({
  fetchComments: vi.fn(),
  createComment: vi.fn(),
  deleteComment: vi.fn(),
  likeComment: vi.fn(),
  unlikeComment: vi.fn(),
}))

vi.mock('@/api/post', () => ({
  fetchPostDetail: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}))

vi.mock('@shared/api/report', () => ({
  createReport: vi.fn(),
  submitAppeal: vi.fn(),
}))

const mockedFetchPostDetail = vi.mocked(fetchPostDetail)
const mockedFetchComments = vi.mocked(fetchComments)
const mockedCreateComment = vi.mocked(createComment)
const mockedDeleteComment = vi.mocked(deleteComment)
const mockedLikeComment = vi.mocked(likeComment)
const mockedUnlikeComment = vi.mocked(unlikeComment)
const mockedLikePost = vi.mocked(likePost)
const mockedUnlikePost = vi.mocked(unlikePost)
const mockedSubmitAppeal = vi.mocked(submitAppeal)

function detail(overrides: Partial<PostDetail> = {}): PostDetail {
  return {
    id: 7,
    topicId: 1,
    topicName: '问答',
    topicRules: '描述清楚问题',
    content: '正文',
    contact: null,
    contactVisible: false,
    images: [],
    anonymousCode: '银杏#7',
    likeCount: 0,
    liked: false,
    commentCount: 0,
    mine: false,
    reviewReason: null,
    takenDown: false,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

function comment(id: number, replies: Comment[] = [], overrides: Partial<Comment> = {}): Comment {
  return {
    id,
    content: `评论 ${id}`,
    anonymousCode: `梧桐#${id}`,
    likeCount: 0,
    liked: false,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    replies,
    ...overrides,
  }
}

/** 一级评论分页结果，与后端 common/web/CursorPage 对应 */
function page(items: Comment[], nextCursor: string | null = null): CursorPage<Comment> {
  return { items, nextCursor }
}

describe('usePostDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载时并发拉取详情与评论首页（游标为 null）', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([comment(1)]))

    const state = usePostDetail(7)
    await state.load()

    expect(mockedFetchPostDetail).toHaveBeenCalledWith(7)
    expect(mockedFetchComments).toHaveBeenCalledWith(7, null)
    expect(state.post.value?.id).toBe(7)
    expect(state.comments.value).toHaveLength(1)
    expect(state.hasMore.value).toBe(false)
    expect(state.loading.value).toBe(false)
  })

  it('详情加载失败时结束 loading，评论保持为空', async () => {
    mockedFetchPostDetail.mockRejectedValue(new Error('网络异常'))
    mockedFetchComments.mockResolvedValue(page([]))

    const state = usePostDetail(7)
    await expect(state.load()).resolves.toBeUndefined()

    expect(state.post.value).toBeNull()
    expect(state.loading.value).toBe(false)
  })

  it('发表评论后重拉首页，并在后端权威计数上把评论数加一', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail({ commentCount: 3 }))
    mockedFetchComments
      .mockResolvedValueOnce(page([comment(1)], 'cursor-1'))
      .mockResolvedValueOnce(page([comment(1), comment(4)], null))
    mockedCreateComment.mockResolvedValue(comment(1))

    const state = usePostDetail(7)
    await state.load()
    const ok = await state.submitComment('你好', null)

    expect(ok).toBe(true)
    expect(mockedCreateComment).toHaveBeenCalledWith(7, { content: '你好', parentId: null })
    // 重拉的是首页（游标重置为 null），而不是翻到下一页
    expect(mockedFetchComments).toHaveBeenLastCalledWith(7, null)
    expect(state.post.value?.commentCount).toBe(4)
    expect(state.submitting.value).toBe(false)
  })

  it('发表评论失败时返回 false，交由页面保留草稿', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([]))
    mockedCreateComment.mockRejectedValue(new Error('网络异常'))

    const state = usePostDetail(7)
    await state.load()
    await expect(state.submitComment('你好', null)).resolves.toBe(false)

    expect(state.submitting.value).toBe(false)
  })

  it('回复二级评论时带上父评论 id', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValueOnce(page([comment(1)])).mockResolvedValueOnce(page([comment(1)]))
    mockedCreateComment.mockResolvedValue(comment(2))

    const state = usePostDetail(7)
    await state.load()
    await state.submitComment('回复内容', 1)

    expect(mockedCreateComment).toHaveBeenCalledWith(7, { content: '回复内容', parentId: 1 })
  })

  it('删除一级评论后按「本身 + 已知回复数」扣减计数并重拉首页', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail({ commentCount: 3 }))
    mockedFetchComments
      .mockResolvedValueOnce(page([comment(1, [comment(2)]), comment(4)]))
      .mockResolvedValueOnce(page([comment(4)]))
    mockedDeleteComment.mockResolvedValue(undefined)

    const state = usePostDetail(7)
    await state.load()
    await state.removeComment(1)

    expect(mockedDeleteComment).toHaveBeenCalledWith(1)
    // 一级评论 1 连同其 1 条回复一并删除：3 - 2 = 1
    expect(state.post.value?.commentCount).toBe(1)
  })

  it('加载更多按游标追加下一页，nextCursor 为 null 时不再有下一页', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments
      .mockResolvedValueOnce(page([comment(1)], 'cursor-1'))
      .mockResolvedValueOnce(page([comment(2)], null))

    const state = usePostDetail(7)
    await state.load()
    expect(state.hasMore.value).toBe(true)

    await state.loadMoreComments()

    expect(mockedFetchComments).toHaveBeenLastCalledWith(7, 'cursor-1')
    expect(state.comments.value.map((item) => item.id)).toEqual([1, 2])
    expect(state.hasMore.value).toBe(false)
    expect(state.loadingMore.value).toBe(false)
  })

  it('末页（nextCursor 为 null）时「加载更多」不发请求', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([comment(1)], null))

    const state = usePostDetail(7)
    await state.load()
    await state.loadMoreComments()

    expect(mockedFetchComments).toHaveBeenCalledTimes(1)
  })

  it('评论点赞能定位到二级回复并写回状态', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([comment(1, [comment(2)])]))
    mockedLikeComment.mockResolvedValue({ likeCount: 3, liked: true })

    const state = usePostDetail(7)
    await state.load()
    await state.toggleCommentLike(2)

    expect(mockedLikeComment).toHaveBeenCalledWith(2)
    expect(state.comments.value[0].replies[0].liked).toBe(true)
    expect(state.comments.value[0].replies[0].likeCount).toBe(3)
  })

  it('已点赞的评论再次点击走取消点赞接口', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([comment(1, [], { liked: true, likeCount: 3 })]))
    mockedUnlikeComment.mockResolvedValue({ likeCount: 2, liked: false })

    const state = usePostDetail(7)
    await state.load()
    await state.toggleCommentLike(1)

    expect(mockedUnlikeComment).toHaveBeenCalledWith(1)
    expect(state.comments.value[0].liked).toBe(false)
    expect(state.comments.value[0].likeCount).toBe(2)
  })

  it('帖子点赞用后端返回值覆盖本地状态', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue(page([]))
    mockedLikePost.mockResolvedValue({ likeCount: 8, liked: true })
    mockedUnlikePost.mockResolvedValue({ likeCount: 7, liked: false })

    const state = usePostDetail(7)
    await state.load()

    await state.toggleLike()
    expect(mockedLikePost).toHaveBeenCalledWith(7)
    expect(state.post.value?.liked).toBe(true)
    expect(state.post.value?.likeCount).toBe(8)

    await state.toggleLike()
    expect(mockedUnlikePost).toHaveBeenCalledWith(7)
    expect(state.post.value?.liked).toBe(false)
  })

  it('详情未加载时不请求点赞接口', async () => {
    const state = usePostDetail(7)
    await state.toggleLike()

    expect(mockedLikePost).not.toHaveBeenCalled()
  })

  it('申诉成功后标记已申诉，页面据此收起申诉表单', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail({ mine: true, takenDown: true }))
    mockedFetchComments.mockResolvedValue(page([]))
    mockedSubmitAppeal.mockResolvedValue(undefined)

    const state = usePostDetail(7)
    await state.load()

    await expect(state.submitAppeal('内容并未违规')).resolves.toBe(true)

    expect(mockedSubmitAppeal).toHaveBeenCalledWith({ postId: 7, reason: '内容并未违规' })
    expect(state.appealed.value).toBe(true)
    expect(state.appealSubmitting.value).toBe(false)
  })

  it('申诉失败（如已超过 7 日）不标记已申诉，交由拦截器提示', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail({ mine: true, takenDown: true }))
    mockedFetchComments.mockResolvedValue(page([]))
    mockedSubmitAppeal.mockRejectedValue(new Error('申诉期已过'))

    const state = usePostDetail(7)
    await state.load()

    await expect(state.submitAppeal('再来一次')).resolves.toBe(false)

    expect(state.appealed.value).toBe(false)
    expect(state.appealSubmitting.value).toBe(false)
  })
})
