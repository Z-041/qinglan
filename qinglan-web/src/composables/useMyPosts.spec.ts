import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchMyPosts } from '@/api/post'
import { useMyPosts } from '@/composables/useMyPosts'
import type { MyPostItem } from '@/types/post'

vi.mock('@/api/post', () => ({ fetchMyPosts: vi.fn() }))

const mockedFetchMyPosts = vi.mocked(fetchMyPosts)

function item(id: number, overrides: Partial<MyPostItem> = {}): MyPostItem {
  return {
    id,
    topicId: 1,
    topicName: '问答',
    content: `第 ${id} 条`,
    images: [],
    anonymousCode: `银杏#${id}`,
    status: 'PENDING',
    reviewReason: null,
    likeCount: 0,
    commentCount: 0,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

describe('useMyPosts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchMyPosts.mockResolvedValue({ items: [item(9), item(8)], nextCursor: '8' })

    const state = useMyPosts()
    await state.load()

    expect(mockedFetchMyPosts).toHaveBeenCalledWith({ cursor: null })
    expect(state.items.value.map((i) => i.id)).toEqual([9, 8])
    expect(state.hasMore.value).toBe(true)
    expect(state.loading.value).toBe(false)
  })

  it('加载更多带上末条游标并追加到列表尾部', async () => {
    mockedFetchMyPosts
      .mockResolvedValueOnce({ items: [item(9)], nextCursor: '9' })
      .mockResolvedValueOnce({ items: [item(7)], nextCursor: null })

    const state = useMyPosts()
    await state.load()
    await state.loadMore()

    expect(mockedFetchMyPosts).toHaveBeenLastCalledWith({ cursor: '9' })
    expect(state.items.value.map((i) => i.id)).toEqual([9, 7])
    expect(state.hasMore.value).toBe(false)
  })

  it('末页时不再请求下一页', async () => {
    mockedFetchMyPosts.mockResolvedValue({ items: [item(3)], nextCursor: null })

    const state = useMyPosts()
    await state.load()
    await state.loadMore()

    expect(mockedFetchMyPosts).toHaveBeenCalledTimes(1)
  })

  it('加载失败时结束 loading 且置 failed，列表为空且不抛错', async () => {
    mockedFetchMyPosts.mockRejectedValue(new Error('网络异常'))

    const state = useMyPosts()
    await expect(state.load()).resolves.toBeUndefined()

    expect(state.loading.value).toBe(false)
    expect(state.failed.value).toBe(true)
    expect(state.items.value).toEqual([])
  })

  it('重试成功后清掉 failed', async () => {
    mockedFetchMyPosts
      .mockRejectedValueOnce(new Error('网络异常'))
      .mockResolvedValueOnce({ items: [item(9)], nextCursor: null })

    const state = useMyPosts()
    await state.load()
    expect(state.failed.value).toBe(true)

    await state.load()

    expect(state.failed.value).toBe(false)
    expect(state.items.value.map((i) => i.id)).toEqual([9])
  })
})
