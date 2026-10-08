import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminPendingPosts } from '@/api/review'
import { usePendingPostQueue } from '@/composables/usePendingPostQueue'
import type { AdminPendingPostItem } from '@/types/review'

vi.mock('@/api/review', () => ({ fetchAdminPendingPosts: vi.fn() }))

const mockedFetchAdminPendingPosts = vi.mocked(fetchAdminPendingPosts)

function pendingPost(postId: number, overrides: Partial<AdminPendingPostItem> = {}): AdminPendingPostItem {
  return {
    postId,
    preview: `第 ${postId} 条待审帖子`,
    targetStatus: 'PENDING',
    submittedAt: '2026-10-06T00:00:00Z',
    reportCount: 0,
    ...overrides,
  }
}

describe('usePendingPostQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchAdminPendingPosts.mockResolvedValue({
      items: [pendingPost(1), pendingPost(2)],
      nextCursor: '1',
    })

    const queue = usePendingPostQueue()
    await queue.refresh()

    expect(mockedFetchAdminPendingPosts).toHaveBeenCalledWith(null)
    expect(queue.items.value.map((row) => row.postId)).toEqual([1, 2])
    expect(queue.hasMore.value).toBe(true)
    expect(queue.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchAdminPendingPosts.mockResolvedValue({ items: [pendingPost(1)], nextCursor: null })

    const queue = usePendingPostQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(queue.hasMore.value).toBe(false)
    expect(mockedFetchAdminPendingPosts).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchAdminPendingPosts
      .mockResolvedValueOnce({ items: [pendingPost(1)], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [pendingPost(2)], nextCursor: null })

    const queue = usePendingPostQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(mockedFetchAdminPendingPosts).toHaveBeenLastCalledWith('cursor-1')
    expect(queue.items.value.map((row) => row.postId)).toEqual([1, 2])
    expect(queue.hasMore.value).toBe(false)
  })

  it('刷新失败时结束 loading 且不抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminPendingPosts.mockRejectedValue(new Error('网络异常'))

    const queue = usePendingPostQueue()
    await expect(queue.refresh()).resolves.toBeUndefined()

    expect(queue.items.value).toEqual([])
    expect(queue.loading.value).toBe(false)
  })

  it('刷新时先清空列表与游标，避免失败后拿旧游标去「加载更多」', async () => {
    mockedFetchAdminPendingPosts.mockResolvedValueOnce({ items: [pendingPost(1)], nextCursor: 'cursor-1' })

    const queue = usePendingPostQueue()
    await queue.refresh()

    mockedFetchAdminPendingPosts.mockRejectedValueOnce(new Error('网络异常'))
    await queue.refresh()

    expect(queue.items.value).toEqual([])
    expect(queue.hasMore.value).toBe(false)
    expect(queue.failed.value).toBe(true)
  })
})
