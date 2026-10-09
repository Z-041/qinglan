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

/** 手动控制何时 resolve，用来构造「响应乱序」的场景 */
function deferredPage() {
  let resolve: (value: { items: PostSummary[]; nextCursor: string | null }) => void = () => {}
  const promise = new Promise<{ items: PostSummary[]; nextCursor: string | null }>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

describe('useFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(9), post(8)], nextCursor: '8' })

    const feed = useFeed()
    await feed.load(null)

    expect(mockedFetchPosts).toHaveBeenCalledWith({ topic: null, sort: 'latest', cursor: null })
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

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: null, sort: 'latest', cursor: '8' })
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

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: 3, sort: 'latest', cursor: null })
    expect(feed.topicId.value).toBe(3)
    expect(feed.posts.value.map((item) => item.id)).toEqual([1])
  })

  it('切换排序重置到第一页并保留当前话题（排序与话题正交）', async () => {
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: null })
      .mockResolvedValueOnce({ items: [post(5)], nextCursor: null })

    const feed = useFeed()
    await feed.load(3)

    await feed.setSort('hot')

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: 3, sort: 'hot', cursor: null })
    expect(feed.sort.value).toBe('hot')
    expect(feed.posts.value.map((item) => item.id)).toEqual([5])
  })

  it('点击当前排序 Tab 不重复发请求', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(9)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    mockedFetchPosts.mockClear()

    await feed.setSort('latest')

    expect(mockedFetchPosts).not.toHaveBeenCalled()
  })

  it('切换到热门后翻页带上当前 sort（游标格式随排序变化，必须一并回传）', async () => {
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: null })
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: 'hot-cursor' })
      .mockResolvedValueOnce({ items: [post(8)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    await feed.setSort('hot')
    await feed.loadMore()

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({
      topic: null,
      sort: 'hot',
      cursor: 'hot-cursor',
    })
    expect(feed.posts.value.map((item) => item.id)).toEqual([9, 8])
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

  it('首屏失败时置 failed，供页面与「真的没有内容」区分开', async () => {
    mockedFetchPosts.mockRejectedValue(new Error('网络异常'))

    const feed = useFeed()
    await feed.load(null)

    expect(feed.failed.value).toBe(true)
  })

  it('重试成功后清掉 failed', async () => {
    mockedFetchPosts
      .mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: null })

    const feed = useFeed()
    await feed.load(null)
    expect(feed.failed.value).toBe(true)

    await feed.refresh()

    expect(feed.failed.value).toBe(false)
    expect(feed.posts.value.map((item) => item.id)).toEqual([9])
  })

  it('切换话题失败时不残留上一话题的列表与游标（否则加载更多会拼出跨话题请求）', async () => {
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9), post(8)], nextCursor: '8' })
      .mockRejectedValueOnce(new Error('网络异常'))

    const feed = useFeed()
    await feed.load(null)
    expect(feed.hasMore.value).toBe(true)

    await feed.load(3)

    expect(feed.posts.value).toEqual([])
    expect(feed.hasMore.value).toBe(false)
    expect(feed.failed.value).toBe(true)
    // 没有 hasMore 就不会出现「加载更多」，也就不会拿旧游标去请求新话题
    await feed.loadMore()
    expect(mockedFetchPosts).toHaveBeenCalledTimes(2)
  })

  it('切换话题时旧响应晚到必须被丢弃（否则会出现 B 高亮却显示 A 的内容）', async () => {
    const first = deferredPage()
    const second = deferredPage()
    mockedFetchPosts.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const feed = useFeed()
    const loadA = feed.load(1)
    const loadB = feed.load(2)

    // 后发的 B 先返回
    second.resolve({ items: [post(20)], nextCursor: null })
    await loadB
    // A 的响应晚到：必须被丢弃，不能覆盖 B 的列表
    first.resolve({ items: [post(10)], nextCursor: null })
    await loadA

    expect(feed.topicId.value).toBe(2)
    expect(feed.posts.value.map((item) => item.id)).toEqual([20])
    expect(feed.loading.value).toBe(false)
  })

  it('加载更多在途时切话题：旧响应不得追加进新话题的列表', async () => {
    const more = deferredPage()
    mockedFetchPosts
      .mockResolvedValueOnce({ items: [post(9)], nextCursor: '9' })
      .mockReturnValueOnce(more.promise)
      .mockResolvedValueOnce({ items: [post(30)], nextCursor: null })

    const feed = useFeed()
    await feed.load(1)

    const loadingMore = feed.loadMore()
    const switching = feed.load(2)

    more.resolve({ items: [post(8)], nextCursor: null })
    await loadingMore
    await switching

    expect(feed.topicId.value).toBe(2)
    expect(feed.posts.value.map((item) => item.id)).toEqual([30])
  })
})
