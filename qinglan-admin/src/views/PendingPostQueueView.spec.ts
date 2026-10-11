import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminMe } from '@/api/admin'
import { approvePost, fetchAdminPendingPosts, fetchAdminReviewContext, rejectPost } from '@/api/review'
import { resolveMessageBox } from '@/composables/useMessageBox'
import { usePermissions } from '@/composables/usePermissions'
import PendingPostQueueView from '@/views/PendingPostQueueView.vue'
import type { AdminPermission } from '@/types/permission'
import type { AdminPendingPostItem, AdminReviewContext } from '@/types/review'

vi.mock('@/api/admin', () => ({ fetchAdminMe: vi.fn() }))
vi.mock('@/api/review', () => ({
  fetchAdminPendingPosts: vi.fn(),
  fetchAdminReviewContext: vi.fn(),
  approvePost: vi.fn(),
  rejectPost: vi.fn(),
}))

const mockedFetchAdminMe = vi.mocked(fetchAdminMe)
const mockedFetchAdminPendingPosts = vi.mocked(fetchAdminPendingPosts)
const mockedFetchAdminReviewContext = vi.mocked(fetchAdminReviewContext)
const mockedApprovePost = vi.mocked(approvePost)
const mockedRejectPost = vi.mocked(rejectPost)

function pendingPost(overrides: Partial<AdminPendingPostItem> = {}): AdminPendingPostItem {
  return {
    postId: 5,
    preview: '待审正文',
    images: [],
    targetStatus: 'PENDING',
    submittedAt: '2026-10-01T00:00:00Z',
    reportCount: 0,
    ...overrides,
  }
}

function context(): AdminReviewContext {
  return {
    targetType: 'POST',
    targetId: 5,
    targetStatus: 'PENDING',
    authorCreditScore: 80,
    authorViolationCount: 0,
    reportCount: 0,
    distinctReporterCount: 0,
    repeatedReporterCount: 0,
    reasons: [],
  }
}

async function mountView(permissions: AdminPermission[] = ['MODERATION_VIEW', 'POST_REVIEW']): Promise<VueWrapper> {
  mockedFetchAdminMe.mockResolvedValue({ userId: 1, role: 'REVIEWER', permissions })
  await usePermissions().load()

  const wrapper = mount(PendingPostQueueView)
  await flushPromises()
  return wrapper
}

async function focusFirstRow(wrapper: VueWrapper) {
  const section = wrapper.find('section')
  await section.trigger('focusin')
  await section.trigger('keydown', { key: 'ArrowDown' })
  await flushPromises()
  return section
}

describe('PendingPostQueueView 键盘与快捷键', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    usePermissions().reset()
    mockedFetchAdminPendingPosts.mockResolvedValue({ items: [pendingPost()], nextCursor: null })
    mockedFetchAdminReviewContext.mockResolvedValue(context())
    mockedApprovePost.mockResolvedValue(undefined)
    mockedRejectPost.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    // 清掉可能残留的理由弹窗（单例状态），避免泄漏到下一条用例
    resolveMessageBox(false)
  })

  it('行焦点：↑ 聚焦首行后面板自动加载', async () => {
    wrapper = await mountView()

    await focusFirstRow(wrapper)

    expect(mockedFetchAdminReviewContext).toHaveBeenCalledWith('POST', 5)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })

  it('快捷键按键随本页动作集渲染：只有 1 通过 / 2 驳回', async () => {
    wrapper = await mountView()
    await focusFirstRow(wrapper)

    expect(wrapper.findAll('kbd').map((node) => node.text())).toEqual(['1', '2'])
  })

  it('无 POST_REVIEW 权限时不渲染按键，按键也不触发动作', async () => {
    wrapper = await mountView(['MODERATION_VIEW'])
    const section = await focusFirstRow(wrapper)

    expect(wrapper.findAll('kbd')).toHaveLength(0)

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    expect(mockedApprovePost).not.toHaveBeenCalled()
  })

  it('「1 通过」与按钮同路径：直接放行并推进到下一条', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    expect(mockedApprovePost).toHaveBeenCalledWith(5)
  })

  it('「2 驳回」仍先弹窗二次确认，不直接执行', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)

    await section.trigger('keydown', { key: '2' })
    await flushPromises()

    expect(mockedRejectPost).not.toHaveBeenCalled()
  })

  it('未聚焦列表时数字键不触发动作', async () => {
    wrapper = await mountView()
    const section = wrapper.find('section')

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    expect(mockedApprovePost).not.toHaveBeenCalled()
  })
})
