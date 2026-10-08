import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminAppeals } from '@/api/review'
import { useAppealQueue } from '@/composables/useAppealQueue'
import type { AdminAppealItem } from '@/types/review'

vi.mock('@/api/review', () => ({ fetchAdminAppeals: vi.fn() }))

const mockedFetchAdminAppeals = vi.mocked(fetchAdminAppeals)

function appeal(appealId: number, overrides: Partial<AdminAppealItem> = {}): AdminAppealItem {
  return {
    appealId,
    targetType: 'POST',
    targetId: 100 + appealId,
    preview: `第 ${appealId} 条申诉`,
    targetStatus: 'TAKEN_DOWN',
    takenDownReason: 'ABUSE: 人身攻击',
    appealReason: '误判',
    appealedAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

describe('useAppealQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchAdminAppeals.mockResolvedValue({ items: [appeal(1), appeal(2)], nextCursor: '1' })

    const queue = useAppealQueue()
    await queue.refresh()

    expect(mockedFetchAdminAppeals).toHaveBeenCalledWith(null)
    expect(queue.items.value.map((row) => row.appealId)).toEqual([1, 2])
    expect(queue.hasMore.value).toBe(true)
    expect(queue.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchAdminAppeals.mockResolvedValue({ items: [appeal(1)], nextCursor: null })

    const queue = useAppealQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(queue.hasMore.value).toBe(false)
    expect(mockedFetchAdminAppeals).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchAdminAppeals
      .mockResolvedValueOnce({ items: [appeal(1)], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [appeal(2)], nextCursor: null })

    const queue = useAppealQueue()
    await queue.refresh()
    await queue.loadMore()

    expect(mockedFetchAdminAppeals).toHaveBeenLastCalledWith('cursor-1')
    expect(queue.items.value.map((row) => row.appealId)).toEqual([1, 2])
    expect(queue.hasMore.value).toBe(false)
  })

  it('刷新失败时结束 loading 且不抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminAppeals.mockRejectedValue(new Error('网络异常'))

    const queue = useAppealQueue()
    await expect(queue.refresh()).resolves.toBeUndefined()

    expect(queue.items.value).toEqual([])
    expect(queue.loading.value).toBe(false)
  })
})
