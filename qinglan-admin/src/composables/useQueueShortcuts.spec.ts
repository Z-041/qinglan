import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { fetchAdminMe } from '@/api/admin'
import { usePermissions } from '@/composables/usePermissions'
import { useQueueShortcuts, type QueueShortcut } from '@/composables/useQueueShortcuts'
import type { AdminMe } from '@/types/admin'

vi.mock('@/api/admin', () => ({ fetchAdminMe: vi.fn() }))

const mockedFetchAdminMe = vi.mocked(fetchAdminMe)

type Action = 'approve' | 'reject' | 'dismiss'

const DEFS: readonly QueueShortcut<Action>[] = [
  { key: '2', action: 'reject', label: '驳回', permission: 'POST_REVIEW' },
  { key: '1', action: 'approve', label: '通过', permission: 'POST_REVIEW' },
  { key: '4', action: 'dismiss', label: '举报不成立', permission: 'REPORT_DISMISS' },
]

function me(permissions: AdminMe['permissions']): AdminMe {
  return { userId: 1, role: 'REVIEWER', permissions }
}

describe('useQueueShortcuts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 模块级权限状态不会在测试间自动复位
    usePermissions().reset()
  })

  it('只保留当前账号有权限的动作，按键查找同样受限', async () => {
    mockedFetchAdminMe.mockResolvedValue(me(['POST_REVIEW']))
    await usePermissions().load()

    const { bindings, actionOf } = useQueueShortcuts(DEFS)

    expect(bindings.value.map((item) => item.key)).toEqual(['1', '2'])
    expect(actionOf('1')).toBe('approve')
    // 无权限的动作既不渲染也不响应
    expect(actionOf('4')).toBeNull()
  })

  it('绑定按触发键排序，提示条读起来与按键顺序一致', async () => {
    mockedFetchAdminMe.mockResolvedValue(me(['POST_REVIEW', 'REPORT_DISMISS']))
    await usePermissions().load()

    const { bindings } = useQueueShortcuts(DEFS)

    expect(bindings.value.map((item) => item.key)).toEqual(['1', '2', '4'])
  })

  it('权限为空时没有任何绑定，任何键都不触发动作', () => {
    const { bindings, actionOf } = useQueueShortcuts(DEFS)

    expect(bindings.value).toEqual([])
    expect(actionOf('1')).toBeNull()
  })

  it('动作集随焦点目标变化时绑定随之更新', async () => {
    mockedFetchAdminMe.mockResolvedValue(me(['POST_REVIEW', 'REPORT_DISMISS']))
    await usePermissions().load()

    const defs = ref<readonly QueueShortcut<Action>[]>(DEFS)
    const { bindings, actionOf } = useQueueShortcuts(defs)
    expect(actionOf('4')).toBe('dismiss')

    defs.value = []
    expect(bindings.value).toEqual([])
    expect(actionOf('4')).toBeNull()
  })
})
