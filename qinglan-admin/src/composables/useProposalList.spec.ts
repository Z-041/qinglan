import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import { fetchAdminProposals, voteProposal } from '@/api/proposals'
import { useProposalList } from '@/composables/useProposalList'
import type { ProposalSummary } from '@/types/proposal'

vi.mock('@/api/proposals', () => ({
  fetchAdminProposals: vi.fn(),
  voteProposal: vi.fn(),
}))

const mockedFetchAdminProposals = vi.mocked(fetchAdminProposals)
const mockedVoteProposal = vi.mocked(voteProposal)

function proposal(proposalId: number, overrides: Partial<ProposalSummary> = {}): ProposalSummary {
  return {
    proposalId,
    targetType: 'POST',
    targetId: 100 + proposalId,
    preview: `第 ${proposalId} 条`,
    action: 'TAKE_DOWN',
    status: 'OPEN',
    agreeCount: 1,
    disagreeCount: 0,
    needed: 3,
    votedCount: 1,
    myDecision: null,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

describe('useProposalList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载第一页并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [proposal(9), proposal(8)], nextCursor: 'c1' })

    const list = useProposalList()
    await list.refresh()

    expect(mockedFetchAdminProposals).toHaveBeenCalledWith('OPEN', false, null)
    expect(list.items.value.map((row) => row.proposalId)).toEqual([9, 8])
    expect(list.hasMore.value).toBe(true)
    expect(list.loading.value).toBe(false)
  })

  it('末页时 nextCursor 为 null，不再请求下一页', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [proposal(1)], nextCursor: null })

    const list = useProposalList()
    await list.refresh()
    await list.loadMore()

    expect(list.hasMore.value).toBe(false)
    expect(mockedFetchAdminProposals).toHaveBeenCalledTimes(1)
  })

  it('加载更多时带上末条游标并追加到列表尾部', async () => {
    mockedFetchAdminProposals
      .mockResolvedValueOnce({ items: [proposal(9)], nextCursor: 'cursor-1' })
      .mockResolvedValueOnce({ items: [proposal(8)], nextCursor: null })

    const list = useProposalList()
    await list.refresh()
    await list.loadMore()

    expect(mockedFetchAdminProposals).toHaveBeenLastCalledWith('OPEN', false, 'cursor-1')
    expect(list.items.value.map((row) => row.proposalId)).toEqual([9, 8])
    expect(list.hasMore.value).toBe(false)
  })

  it('切换状态筛选会重拉第一页并带上新状态', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [], nextCursor: null })

    const list = useProposalList()
    list.status.value = 'ESCALATED'
    await nextTick()

    expect(mockedFetchAdminProposals).toHaveBeenCalledWith('ESCALATED', false, null)
  })

  it('打开「只看我发起的」会重拉第一页并带上 mine=true', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [], nextCursor: null })

    const list = useProposalList()
    list.mine.value = true
    await nextTick()

    expect(mockedFetchAdminProposals).toHaveBeenCalledWith('OPEN', true, null)
  })

  it('投票成功后重拉第一页并返回 true', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [proposal(1)], nextCursor: null })
    mockedVoteProposal.mockResolvedValue({
      status: 'OPEN',
      agreeCount: 2,
      disagreeCount: 0,
      needed: 3,
      myDecision: 'AGREE',
    })

    const list = useProposalList()
    await list.refresh()

    await expect(list.vote(1, 'AGREE')).resolves.toBe(true)
    expect(mockedVoteProposal).toHaveBeenCalledWith(1, { decision: 'AGREE' })
    // 首屏 1 次 + 投票后刷新 1 次
    expect(mockedFetchAdminProposals).toHaveBeenCalledTimes(2)
    expect(list.submitting.value).toBe(false)
  })

  it('投票失败时返回 false 且不刷新列表（错误已由拦截器提示）', async () => {
    mockedFetchAdminProposals.mockResolvedValue({ items: [proposal(1)], nextCursor: null })
    mockedVoteProposal.mockRejectedValue(new Error('网络异常'))

    const list = useProposalList()
    await list.refresh()

    await expect(list.vote(1, 'DISAGREE')).resolves.toBe(false)
    expect(mockedFetchAdminProposals).toHaveBeenCalledTimes(1)
    expect(list.submitting.value).toBe(false)
  })

  it('刷新失败时结束 loading、标记 failed 且不抛错', async () => {
    mockedFetchAdminProposals.mockRejectedValue(new Error('网络异常'))

    const list = useProposalList()
    await expect(list.refresh()).resolves.toBeUndefined()

    expect(list.items.value).toEqual([])
    expect(list.loading.value).toBe(false)
    expect(list.failed.value).toBe(true)
  })
})
