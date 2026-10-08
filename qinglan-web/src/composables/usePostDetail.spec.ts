import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createComment, deleteComment, fetchComments, likeComment, unlikeComment } from '@/api/comment'
import { fetchPostDetail, likePost, unlikePost } from '@/api/post'
import { submitAppeal } from '@shared/api/report'
import { usePostDetail } from '@/composables/usePostDetail'
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

describe('usePostDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载时并发拉取详情与评论列表', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue([comment(1)])

    const state = usePostDetail(7)
    await state.load()

    expect(mockedFetchPostDetail).toHaveBeenCalledWith(7)
    expect(mockedFetchComments).toHaveBeenCalledWith(7)
    expect(state.post.value?.id).toBe(7)
    expect(state.comments.value).toHaveLength(1)
    expect(state.loading.value).toBe(false)
  })

  it('详情加载失败时结束 loading，评论保持为空', async () => {
    mockedFetchPostDetail.mockRejectedValue(new Error('网络异常'))
    mockedFetchComments.mockResolvedValue([])

    const state = usePostDetail(7)
    await expect(state.load()).resolves.toBeUndefined()

    expect(state.post.value).toBeNull()
    expect(state.loading.value).toBe(false)
  })

  it('发表评论后重拉列表，并按实际条数（含回复）同步评论数', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([comment(1, [comment(2), comment(3)]), comment(4)])
    mockedCreateComment.mockResolvedValue(comment(1))

    const state = usePostDetail(7)
    await state.load()
    const ok = await state.submitComment('你好', null)

    expect(ok).toBe(true)
    expect(mockedCreateComment).toHaveBeenCalledWith(7, { content: '你好', parentId: null })
    // 两条一级评论 + 其中一条的两个回复 = 4
    expect(state.post.value?.commentCount).toBe(4)
    expect(state.submitting.value).toBe(false)
  })

  it('发表评论失败时返回 false，交由页面保留草稿', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue([])
    mockedCreateComment.mockRejectedValue(new Error('网络异常'))

    const state = usePostDetail(7)
    await state.load()
    await expect(state.submitComment('你好', null)).resolves.toBe(false)

    expect(state.submitting.value).toBe(false)
  })

  it('回复二级评论时带上父评论 id', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValueOnce([comment(1)]).mockResolvedValueOnce([comment(1)])
    mockedCreateComment.mockResolvedValue(comment(2))

    const state = usePostDetail(7)
    await state.load()
    await state.submitComment('回复内容', 1)

    expect(mockedCreateComment).toHaveBeenCalledWith(7, { content: '回复内容', parentId: 1 })
  })

  it('删除评论后重拉列表并同步评论数', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments
      .mockResolvedValueOnce([comment(1, [comment(2)]), comment(4)])
      .mockResolvedValueOnce([comment(4)])
    mockedDeleteComment.mockResolvedValue(undefined)

    const state = usePostDetail(7)
    await state.load()
    await state.removeComment(1)

    expect(mockedDeleteComment).toHaveBeenCalledWith(1)
    expect(state.post.value?.commentCount).toBe(1)
  })

  it('评论点赞能定位到二级回复并写回状态', async () => {
    mockedFetchPostDetail.mockResolvedValue(detail())
    mockedFetchComments.mockResolvedValue([comment(1, [comment(2)])])
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
    mockedFetchComments.mockResolvedValue([comment(1, [], { liked: true, likeCount: 3 })])
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
    mockedFetchComments.mockResolvedValue([])
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
    mockedFetchComments.mockResolvedValue([])
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
    mockedFetchComments.mockResolvedValue([])
    mockedSubmitAppeal.mockRejectedValue(new Error('申诉期已过'))

    const state = usePostDetail(7)
    await state.load()

    await expect(state.submitAppeal('再来一次')).resolves.toBe(false)

    expect(state.appealed.value).toBe(false)
    expect(state.appealSubmitting.value).toBe(false)
  })
})
