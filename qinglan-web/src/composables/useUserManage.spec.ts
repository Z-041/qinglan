import { beforeEach, describe, expect, it, vi } from 'vitest'

import { banUser, fetchAdminUsers } from '@/api/admin'
import { createEmptyFilters, toQuery, useUserManage } from '@/composables/useUserManage'
import type { AdminUserItem } from '@/types/admin'

// 工厂必须覆盖 useUserManage 从该模块导入的全部符号，
// 否则 Vitest 会在访问缺失导出时直接报错
vi.mock('@/api/admin', () => ({
  fetchAdminUsers: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn(),
  adjustCredit: vi.fn(),
}))

const mockedFetchAdminUsers = vi.mocked(fetchAdminUsers)
const mockedBanUser = vi.mocked(banUser)

function user(id: number, overrides: Partial<AdminUserItem> = {}): AdminUserItem {
  return {
    id,
    role: 'STUDENT',
    status: 'ACTIVE',
    creditScore: 100,
    bannedUntil: null,
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

describe('toQuery', () => {
  it('全部不限时所有筛选字段为 null，不把空条件发给后端', () => {
    expect(toQuery(createEmptyFilters(), null)).toEqual({
      cursor: null,
      status: null,
      role: null,
      minCredit: null,
      maxCredit: null,
      createdFrom: null,
      createdTo: null,
    })
  })

  it('信用分转数字、注册时间转 ISO-8601 UTC，游标原样透传', () => {
    const query = toQuery(
      {
        status: 'BANNED',
        role: 'STUDENT',
        minCredit: '0',
        maxCredit: '60',
        createdFrom: '2026-10-01T08:00',
        createdTo: '',
      },
      '1767225600000:7',
    )

    expect(query.cursor).toBe('1767225600000:7')
    expect(query.status).toBe('BANNED')
    expect(query.role).toBe('STUDENT')
    expect(query.minCredit).toBe(0)
    expect(query.maxCredit).toBe(60)
    // datetime-local 是本地时间且无时区，后端要 Instant
    expect(query.createdFrom).toBe(new Date('2026-10-01T08:00').toISOString())
    expect(query.createdTo).toBeNull()
  })

  it('信用分填了非整数按「不限」处理，不把 NaN 发给后端', () => {
    expect(toQuery({ ...createEmptyFilters(), minCredit: 'abc' }, null).minCredit).toBeNull()
    expect(toQuery({ ...createEmptyFilters(), maxCredit: '1.5' }, null).maxCredit).toBeNull()
  })

  it('筛选值由输入事件写入（恒为 string）时仍解析为数字，不因绑定被改回 v-model 而失灵', () => {
    const query = toQuery({ ...createEmptyFilters(), minCredit: '110', maxCredit: '200' }, null)

    expect(query.minCredit).toBe(110)
    expect(query.maxCredit).toBe(200)
  })
})

describe('useUserManage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('刷新时带当前筛选条件与空游标，并据 nextCursor 判定是否还有下一页', async () => {
    mockedFetchAdminUsers.mockResolvedValue({ items: [user(2), user(1)], nextCursor: 'c1' })

    const manage = useUserManage()
    manage.filters.value.status = 'BANNED'
    await manage.refresh()

    expect(mockedFetchAdminUsers).toHaveBeenCalledWith(
      expect.objectContaining({ cursor: null, status: 'BANNED' }),
    )
    expect(manage.items.value.map((row) => row.id)).toEqual([2, 1])
    expect(manage.hasMore.value).toBe(true)
    expect(manage.loading.value).toBe(false)
  })

  it('加载更多带上末条游标并追加到列表尾部', async () => {
    mockedFetchAdminUsers
      .mockResolvedValueOnce({ items: [user(2)], nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: [user(1)], nextCursor: null })

    const manage = useUserManage()
    await manage.refresh()
    await manage.loadMore()

    expect(mockedFetchAdminUsers).toHaveBeenLastCalledWith(expect.objectContaining({ cursor: 'c1' }))
    expect(manage.items.value.map((row) => row.id)).toEqual([2, 1])
    expect(manage.hasMore.value).toBe(false)
  })

  it('末页后不再请求下一页', async () => {
    mockedFetchAdminUsers.mockResolvedValue({ items: [user(1)], nextCursor: null })

    const manage = useUserManage()
    await manage.refresh()
    await manage.loadMore()

    expect(mockedFetchAdminUsers).toHaveBeenCalledTimes(1)
  })

  it('重置清空筛选并重新查询第一页', async () => {
    mockedFetchAdminUsers.mockResolvedValue({ items: [], nextCursor: null })

    const manage = useUserManage()
    manage.filters.value = { ...createEmptyFilters(), status: 'BANNED', minCredit: '10' }
    await manage.reset()

    expect(manage.filters.value).toEqual(createEmptyFilters())
    expect(mockedFetchAdminUsers).toHaveBeenCalledWith(
      expect.objectContaining({ status: null, minCredit: null }),
    )
  })

  it('刷新失败时结束 loading 且不抛错（错误已由拦截器提示）', async () => {
    mockedFetchAdminUsers.mockRejectedValue(new Error('网络异常'))

    const manage = useUserManage()
    await expect(manage.refresh()).resolves.toBeUndefined()

    expect(manage.items.value).toEqual([])
    expect(manage.loading.value).toBe(false)
  })

  it('封禁成功返回 true、失败返回 false，两种情况都复位 submitting', async () => {
    mockedBanUser.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('失败'))

    const manage = useUserManage()

    await expect(manage.ban(7, { days: 3, reason: '刷屏' })).resolves.toBe(true)
    await expect(manage.ban(7, { days: 3, reason: '刷屏' })).resolves.toBe(false)
    expect(mockedBanUser).toHaveBeenLastCalledWith(7, { days: 3, reason: '刷屏' })
    expect(manage.submitting.value).toBe(false)
  })
})
