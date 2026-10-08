import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminStats } from '@/api/admin'
import DashboardView from '@/views/DashboardView.vue'
import type { AdminStats } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminStats: vi.fn() }))

const mockedFetchAdminStats = vi.mocked(fetchAdminStats)

function emptyStats(): AdminStats {
  return {
    content: { totalPosts: 0, pendingPosts: 0, takenDownPosts: 0, todayNewPosts: 0 },
    moderation: { pendingReports: 0, pendingAppeals: 0, todayHandled: 0, last7DaysHandled: [] },
    users: { totalUsers: 0, bannedUsers: 0, todayNewUsers: 0 },
  }
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败时显示失败态与重试，而不是「暂无数据」', async () => {
    mockedFetchAdminStats.mockRejectedValue(new Error('网络异常'))

    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('看板数据没能加载出来')
    expect(wrapper.findAll('button').some((button) => button.text() === '重试')).toBe(true)
    // 关键：网络故障不得被说成「真的没有数据」
    expect(wrapper.text()).not.toContain('暂无数据')
  })

  it('加载成功但确实无数据时才显示「暂无数据」', async () => {
    mockedFetchAdminStats.mockResolvedValue(emptyStats())

    const wrapper = mount(DashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.text()).not.toContain('看板数据没能加载出来')
  })
})
