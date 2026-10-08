import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminMe } from '@/api/admin'
import { usePermissions } from '@/composables/usePermissions'
import type { AdminMe } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminMe: vi.fn() }))

const mockedFetchAdminMe = vi.mocked(fetchAdminMe)

function me(overrides: Partial<AdminMe> = {}): AdminMe {
  return {
    userId: 9,
    role: 'REVIEWER',
    permissions: ['MODERATION_VIEW', 'POST_REVIEW', 'STATS_VIEW'],
    ...overrides,
  }
}

describe('usePermissions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 模块级状态不会在测试之间自动复位（R-26 的教训），必须显式清
    usePermissions().reset()
  })

  it('加载后按权限点判定，未授予的为 false', async () => {
    mockedFetchAdminMe.mockResolvedValue(me())

    const { permissions, role, loaded, can, load } = usePermissions()
    await load()

    expect(permissions.value).toEqual(['MODERATION_VIEW', 'POST_REVIEW', 'STATS_VIEW'])
    expect(role.value).toBe('REVIEWER')
    expect(loaded.value).toBe(true)
    expect(can('POST_REVIEW')).toBe(true)
    expect(can('USER_MANAGE')).toBe(false)
  })

  it('多个组件读到同一份状态：菜单与按钮不会各拿一份（否则会出现菜单灰了按钮还在）', async () => {
    mockedFetchAdminMe.mockResolvedValue(me({ permissions: ['STATS_VIEW'] }))

    await usePermissions().load()

    expect(usePermissions().can('STATS_VIEW')).toBe(true)
    expect(usePermissions().permissions.value).toEqual(['STATS_VIEW'])
  })

  it('reset 清空权限与角色，避免上一份权限漏给下一个登录的账号', async () => {
    mockedFetchAdminMe.mockResolvedValue(me())
    const state = usePermissions()
    await state.load()

    state.reset()

    expect(state.permissions.value).toEqual([])
    expect(state.role.value).toBeNull()
    expect(state.loaded.value).toBe(false)
    expect(state.can('POST_REVIEW')).toBe(false)
  })

  it('加载失败时向上抛，且不写入半截状态（路由守卫据此回登录页）', async () => {
    mockedFetchAdminMe.mockRejectedValue(new Error('网络异常'))
    const state = usePermissions()

    await expect(state.load()).rejects.toThrow('网络异常')

    expect(state.loaded.value).toBe(false)
    expect(state.permissions.value).toEqual([])
  })
})
