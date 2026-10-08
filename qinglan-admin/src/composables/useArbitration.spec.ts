import { beforeEach, describe, expect, it, vi } from 'vitest'

import { arbitrateProposal, fetchEscalatedProposals } from '@/api/proposals'
import { useArbitration } from '@/composables/useArbitration'
import type { EscalatedProposal } from '@/types/proposal'

vi.mock('@/api/proposals', () => ({
  fetchEscalatedProposals: vi.fn(),
  arbitrateProposal: vi.fn(),
}))

const mockedFetchEscalated = vi.mocked(fetchEscalatedProposals)
const mockedArbitrate = vi.mocked(arbitrateProposal)

function escalated(proposalId: number, overrides: Partial<EscalatedProposal> = {}): EscalatedProposal {
  return {
    proposalId,
    targetType: 'POST',
    targetId: 100 + proposalId,
    preview: `第 ${proposalId} 条`,
    action: 'TAKE_DOWN',
    status: 'ESCALATED',
    agreeCount: 1,
    disagreeCount: 2,
    needed: 4,
    votedCount: 3,
    createdAt: '2026-10-06T00:00:00Z',
    votes: [{ voterId: 7, decision: 'AGREE', createdAt: '2026-10-06T01:00:00Z' }],
    ...overrides,
  }
}

describe('useArbitration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchEscalated.mockResolvedValue({ items: [escalated(9), escalated(8)], nextCursor: 'c1' })

    const arbitration = useArbitration()
    await arbitration.refresh()

    expect(mockedFetchEscalated).toHaveBeenCalledWith(null)
    expect(arbitration.items.value.map((row) => row.proposalId)).toEqual([9, 8])
    expect(arbitration.hasMore.value).toBe(true)
    expect(arbitration.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchEscalated.mockResolvedValue({ items: [escalated(1)], nextCursor: null })

    const arbitration = useArbitration()
    await arbitration.refresh()
    await arbitration.loadMore()

    expect(arbitration.hasMore.value).toBe(false)
    expect(mockedFetchEscalated).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchEscalated
      .mockResolvedValueOnce({ items: [escalated(9)], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [escalated(8)], nextCursor: null })

    const arbitration = useArbitration()
    await arbitration.refresh()
    await arbitration.loadMore()

    expect(mockedFetchEscalated).toHaveBeenLastCalledWith('cursor-1')
    expect(arbitration.items.value.map((row) => row.proposalId)).toEqual([9, 8])
    expect(arbitration.hasMore.value).toBe(false)
  })

  it('裁决成功后重拉第一页并返回 true', async () => {
    mockedFetchEscalated.mockResolvedValue({ items: [escalated(1)], nextCursor: null })
    mockedArbitrate.mockResolvedValue(undefined)

    const arbitration = useArbitration()
    await arbitration.refresh()

    await expect(arbitration.arbitrate(1, { decision: 'GRANT', reason: '证据充分' })).resolves.toBe(true)
    expect(mockedArbitrate).toHaveBeenCalledWith(1, { decision: 'GRANT', reason: '证据充分' })
    // 首屏 1 次 + 裁决后刷新 1 次
    expect(mockedFetchEscalated).toHaveBeenCalledTimes(2)
    expect(arbitration.submitting.value).toBe(false)
  })

  it('裁决失败时返回 false 且不刷新列表（错误已由拦截器提示）', async () => {
    mockedFetchEscalated.mockResolvedValue({ items: [escalated(1)], nextCursor: null })
    mockedArbitrate.mockRejectedValue(new Error('网络异常'))

    const arbitration = useArbitration()
    await arbitration.refresh()

    await expect(arbitration.arbitrate(1, { decision: 'DENY', reason: '证据不足' })).resolves.toBe(false)
    expect(mockedFetchEscalated).toHaveBeenCalledTimes(1)
    expect(arbitration.submitting.value).toBe(false)
  })

  it('刷新失败时结束 loading、标记 failed 且不抛错', async () => {
    mockedFetchEscalated.mockRejectedValue(new Error('网络异常'))

    const arbitration = useArbitration()
    await expect(arbitration.refresh()).resolves.toBeUndefined()

    expect(arbitration.items.value).toEqual([])
    expect(arbitration.loading.value).toBe(false)
    expect(arbitration.failed.value).toBe(true)
  })
})
