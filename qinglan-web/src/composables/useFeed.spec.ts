import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPosts, likePost, unlikePost } from '@/api/post'
import { useFeed } from '@/composables/useFeed'
import type { PostSummary } from '@/types/post'

vi.mock('@/api/post', () => ({
  fetchPosts: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}))

const mockedFetchPosts = vi.mocked(fetchPosts)
const mockedLikePost = vi.mocked(likePost)
const mockedUnlikePost = vi.mocked(unlikePost)

function post(id: number, overrides: Partial<PostSummary> = {}): PostSummary {
  return {
    id,
    topicId: 1,
    topicName: '问答',
    content: `第 ${id} 条`,
    images: [],
    anonymousCode: `银杏#${id}`,
    likeCount: 0,
    liked: false,
    commentCount: 0,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

describe('useFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(9), post(8)], nextCursor: '8' })

    const feed = useFeed()
    await feed.load(null)

    expect(mockedFetchPosts).toHaveBeenCalledWith({ topic: null })
    expect(feed.posts.value.map((item) => item.id)).toEqual([9, 8])
    expect(feed.hasMore.value).toBe(true)
    expect(feed.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(3)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    await feed.loadMore()

    expect(feed.hasMore.value).toBe(false)
    expect(mockedFetchPosts).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9), post(8)], nextCursor: '8' })
      .mockResolvedValueOnce({ items: [post(7)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    await feed.loadMore()

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: null, cursor: '8' })
    expect(feed.posts.value.map((item) => item.id)).toEqual([9, 8, 7])
    expect(feed.hasMore.value).toBe(false)
  })

  it('切换话题时重置列表与游标', async () => {
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: '9' })
      .mockResolvedValueOnce({ items: [post(1)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    await feed.load(3)

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: 3 })
    expect(feed.topicId.value).toBe(3)
    expect(feed.posts.value.map((item) => item.id)).toEqual([1])
  })

  it('点赞用后端返回值覆盖本地计数，取消点赞走 DELETE 接口', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(9)], nextCursor: null })
    mockedLikePost.mockResolvedValue({ likeCount: 5, liked: true })
    mockedUnlikePost.mockResolvedValue({ likeCount: 4, liked: false })

    const feed = useFeed()
    await feed.load(null)
    const target = feed.posts.value[0]

    await feed.toggleLike(target)
    expect(mockedLikePost).toHaveBeenCalledWith(9)
    expect(target.liked).toBe(true)
    expect(target.likeCount).toBe(5)

    await feed.toggleLike(target)
    expect(mockedUnlikePost).toHaveBeenCalledWith(9)
    expect(target.liked).toBe(false)
    expect(target.likeCount).toBe(4)
  })

  it('点赞失败时保持原状态且不向外抛错', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(9)], nextCursor: null })
    mockedLikePost.mockRejectedValue(new Error('网络异常'))

    const feed = useFeed()
    await feed.load(null)
    await expect(feed.toggleLike(feed.posts.value[0])).resolves.toBeUndefined()

    expect(feed.posts.value[0].liked).toBe(false)
    expect(feed.posts.value[0].likeCount).toBe(0)
  })

  it('加载失败时结束 loading 且列表仍为空，不对外抛错', async () => {
    mockedFetchPosts.mockRejectedValue(new Error('网络异常'))

    const feed = useFeed()
    await expect(feed.load(null)).resolves.toBeUndefined()

    expect(feed.loading.value).toBe(false)
    expect(feed.posts.value).toEqual([])
  })
})
