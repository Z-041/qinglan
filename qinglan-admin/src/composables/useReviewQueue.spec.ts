import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminReviewQueue } from '@/api/review'
import { isReportOverdue, useReviewQueue } from '@/composables/useReviewQueue'
import type { AdminQueueItem } from '@/types/review'

vi.mock('@/api/review', () => ({ fetchAdminReviewQueue: vi.fn() }))

const mockedFetchAdminReviewQueue = vi.mocked(fetchAdminReviewQueue)

function item(targetId: number, overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return {
    targetType: 'POST',
    targetId,
    preview: `第 ${targetId} 条`,
    images: [],
    targetStatus: 'PENDING',
    reportCount: 3,
    firstReportedAt: '2026-10-06T00:00:00Z',
    reasons: ['ABUSE'],
    details: [],
    proposal: null,
    ...overrides,
  }
}

describe('isReportOverdue', () => {
  const now = Date.parse('2026-10-06T12:00:00Z')

  it('首报距今超过 24 小时视为超时', () => {
    expect(isReportOverdue('2026-10-05T11:59:00Z', now)).toBe(true)
    expect(isReportOverdue('2026-10-05T12:01:00Z', now)).toBe(false)
  })
})

describe('useReviewQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchAdminReviewQueue.mockResolvedValue({ items: [item(9), item(8)], nextCursor: '3:0:8:0' })

    const queue = useReviewQueue()
    await queue.refresh()

    expect(mockedFetchAdminReviewQueue).toHaveBeenCalledWith(null)
    expect(queue.items.value.map((row) => row.targetId)).toEqual([9, 8])
    expect(queue.hasMore.value).toBe(true)
    expect(queue.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchAdminReviewQueue.mockResolvedValue({ items: [item(1)], nextCursor: null })

    const queue = useReviewQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(queue.hasMore.value).toBe(false)
    expect(mockedFetchAdminReviewQueue).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchAdminReviewQueue
      .mockResolvedValueOnce({ items: [item(9)], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [item(8)], nextCursor: null })

    const queue = useReviewQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(mockedFetchAdminReviewQueue).toHaveBeenLastCalledWith('cursor-1')
    expect(queue.items.value.map((row) => row.targetId)).toEqual([9, 8])
    expect(queue.hasMore.value).toBe(false)
  })

  it('刷新失败时结束 loading 且不抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminReviewQueue.mockRejectedValue(new Error('网络异常'))

    const queue = useReviewQueue()
    await expect(queue.refresh()).resolves.toBeUndefined()

    expect(queue.items.value).toEqual([])
    expect(queue.loading.value).toBe(false)
  })
})
