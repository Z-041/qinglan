import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminTodoCounts } from '@/api/review'
import { buildTodoGroups, TODO_POLL_INTERVAL_MS, useTodoCounts } from '@/composables/useTodoCounts'

vi.mock('@/api/review', () => ({ fetchAdminTodoCounts: vi.fn() }))

const mockedFetchAdminTodoCounts = vi.mocked(fetchAdminTodoCounts)

describe('buildTodoGroups', () => {
  it('字段存在且有权：按定义顺序生成分组（含计数与跳转路由）', () => {
    const groups = buildTodoGroups(
      { pendingPosts: 2, pendingReports: 3, proposable: 1, escalated: 4 },
      ['MODERATION_VIEW', 'PROPOSAL_VOTE', 'PROPOSAL_ARBITRATE'],
    )

    expect(groups.map((group) => group.key)).toEqual([
      'pendingPosts',
      'pendingReports',
      'proposable',
      'escalated',
    ])
    expect(groups[0]).toMatchObject({ label: '新待审', count: 2, routeName: 'admin-pending-posts' })
    expect(groups[3]).toMatchObject({ label: '待裁决', count: 4, routeName: 'admin-arbitration' })
  })

  it('字段缺失（无权限）的分组不展示，即使计数存在', () => {
    // 后端对无权限的组不返回字段：只有 pendingPosts 存在
    const groups = buildTodoGroups({ pendingPosts: 5 }, ['MODERATION_VIEW', 'PROPOSAL_VOTE'])

    expect(groups.map((group) => group.key)).toEqual(['pendingPosts'])
  })

  it('有权但后端仍返回了字段时按权限点复核：无 PROPOSAL_VOTE 不展示待表决', () => {
    const groups = buildTodoGroups({ proposable: 2 }, ['MODERATION_VIEW'])

    expect(groups.map((group) => group.key)).not.toContain('proposable')
    expect(groups).toHaveLength(0)
  })

  it('计数值为 0 仍然展示（0 代表「确有权限且当前无待办」，与字段缺失不同）', () => {
    const groups = buildTodoGroups({ pendingPosts: 0 }, ['MODERATION_VIEW'])

    expect(groups).toHaveLength(1)
    expect(groups[0].count).toBe(0)
  })
})

describe('useTodoCounts', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('load 拉取待办计数', async () => {
    mockedFetchAdminTodoCounts.mockResolvedValue({ pendingPosts: 2, pendingReports: 1 })

    const todo = useTodoCounts()
    await todo.load()

    expect(todo.counts.value.pendingPosts).toBe(2)
    expect(todo.counts.value.pendingReports).toBe(1)
  })

  it('start 立即拉取一次并按间隔轮询；stop 停止', async () => {
    mockedFetchAdminTodoCounts.mockResolvedValue({ pendingPosts: 1 })

    const todo = useTodoCounts()
    todo.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(mockedFetchAdminTodoCounts).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(TODO_POLL_INTERVAL_MS)
    expect(mockedFetchAdminTodoCounts).toHaveBeenCalledTimes(2)

    todo.stop()
    await vi.advanceTimersByTimeAsync(TODO_POLL_INTERVAL_MS * 2)
    expect(mockedFetchAdminTodoCounts).toHaveBeenCalledTimes(2)
  })

  it('页面不可见时不轮询，切回可见恢复', async () => {
    mockedFetchAdminTodoCounts.mockResolvedValue({ pendingPosts: 1 })
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true })

    const todo = useTodoCounts()
    todo.start()
    await vi.advanceTimersByTimeAsync(TODO_POLL_INTERVAL_MS * 2)
    expect(mockedFetchAdminTodoCounts).not.toHaveBeenCalled()

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(0)
    expect(mockedFetchAdminTodoCounts).toHaveBeenCalledTimes(1)

    todo.stop()
  })

  it('clear 把某组计数本地清零（其它组不动）', async () => {
    mockedFetchAdminTodoCounts.mockResolvedValue({ pendingPosts: 3, pendingReports: 2 })

    const todo = useTodoCounts()
    await todo.load()
    todo.clear('pendingPosts')

    expect(todo.counts.value.pendingPosts).toBe(0)
    expect(todo.counts.value.pendingReports).toBe(2)
  })
})
