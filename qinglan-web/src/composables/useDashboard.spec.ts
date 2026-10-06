import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminStats } from '@/api/admin'
import { useDashboard } from '@/composables/useDashboard'
import type { AdminStats } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminStats: vi.fn() }))

const mockedFetchAdminStats = vi.mocked(fetchAdminStats)

function stats(): AdminStats {
  return {
    content: { totalPosts: 10, pendingPosts: 2, takenDownPosts: 1, todayNewPosts: 3 },
    moderation: {
      pendingReports: 4,
      pendingAppeals: 1,
      todayHandled: 5,
      last7DaysHandled: Array.from({ length: 7 }, (_, index) => ({
        date: `2026-10-0${index + 1}`,
        count: index,
      })),
    },
    users: { totalUsers: 100, bannedUsers: 6, todayNewUsers: 7 },
  }
}

describe('useDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载后写入统计并结束 loading', async () => {
    mockedFetchAdminStats.mockResolvedValue(stats())

    const dashboard = useDashboard()
    await dashboard.load()

    expect(mockedFetchAdminStats).toHaveBeenCalledTimes(1)
    expect(dashboard.stats.value?.content.totalPosts).toBe(10)
    expect(dashboard.stats.value?.moderation.last7DaysHandled).toHaveLength(7)
    expect(dashboard.loading.value).toBe(false)
  })

  it('加载失败时 stats 保持 null，且不向外抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminStats.mockRejectedValue(new Error('网络异常'))

    const dashboard = useDashboard()
    await expect(dashboard.load()).resolves.toBeUndefined()

    expect(dashboard.stats.value).toBeNull()
    expect(dashboard.loading.value).toBe(false)
  })
})
