import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminMe } from '@/api/admin'
import {
  fetchAdminReviewContext,
  fetchAdminReviewHistory,
  fetchAdminReviewQueue,
  takeDownPost,
} from '@/api/review'
import { usePermissions } from '@/composables/usePermissions'
import ReviewQueueView from '@/views/ReviewQueueView.vue'
import type { AdminPermission } from '@/types/permission'
import type { AdminQueueItem, AdminReviewContext, AdminReviewHistory } from '@/types/review'

vi.mock('@/api/admin', () => ({ fetchAdminMe: vi.fn() }))
vi.mock('@/api/review', () => ({
  fetchAdminReviewQueue: vi.fn(),
  fetchAdminReviewContext: vi.fn(),
  fetchAdminReviewHistory: vi.fn(),
  approvePost: vi.fn(),
  rejectPost: vi.fn(),
  takeDownPost: vi.fn(),
  restorePost: vi.fn(),
  hidePostContact: vi.fn(),
  showPostContact: vi.fn(),
  removeComment: vi.fn(),
  dismissReports: vi.fn(),
  upholdReports: vi.fn(),
  acceptAppeal: vi.fn(),
  rejectAppeal: vi.fn(),
}))

const mockedFetchAdminMe = vi.mocked(fetchAdminMe)
const mockedFetchAdminReviewQueue = vi.mocked(fetchAdminReviewQueue)
const mockedFetchAdminReviewContext = vi.mocked(fetchAdminReviewContext)
const mockedFetchAdminReviewHistory = vi.mocked(fetchAdminReviewHistory)
const mockedTakeDownPost = vi.mocked(takeDownPost)

/** 弹窗 / 抽屉走 Teleport，单测换成仅在 open 时渲染内容的桩，据此断言「弹窗是否打开」 */
const ElDialogStub = {
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  template:
    '<div v-if="modelValue" class="el-dialog-stub" :data-title="title"><slot /><slot name="footer" /></div>',
}
const ElDrawerStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue" class="el-drawer-stub"><slot /></div>',
}
/** el-switch 换成可点的桩，测试据此切换「只看超时」 */
const ElSwitchStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<button class="el-switch-stub" @click="$emit(\'update:modelValue\', !modelValue)" />',
}

const ALL_PERMISSIONS: AdminPermission[] = ['MODERATION_VIEW', 'POST_REVIEW', 'PROPOSAL_CREATE', 'REPORT_DISMISS']

function queueItem(overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return {
    targetType: 'POST',
    targetId: 5,
    preview: '待处理正文',
    images: [],
    targetStatus: 'PENDING',
    reportCount: 2,
    firstReportedAt: '2026-10-01T00:00:00Z',
    reasons: ['ABUSE'],
    details: [],
    proposal: null,
    ...overrides,
  }
}

function context(overrides: Partial<AdminReviewContext> = {}): AdminReviewContext {
  return {
    targetType: 'POST',
    targetId: 5,
    targetStatus: 'PENDING',
    authorCreditScore: 80,
    authorViolationCount: 1,
    reportCount: 2,
    distinctReporterCount: 2,
    repeatedReporterCount: 0,
    reasons: ['ABUSE'],
    ...overrides,
  }
}

function history(): AdminReviewHistory {
  return {
    authorRecentPosts: [{ id: 11, status: 'PENDING', createdAt: '2026-10-01T00:00:00Z' }],
    targetDispositions: [],
  }
}

async function mountView(permissions: AdminPermission[] = ALL_PERMISSIONS): Promise<VueWrapper> {
  mockedFetchAdminMe.mockResolvedValue({ userId: 1, role: 'REVIEWER', permissions })
  await usePermissions().load()

  const wrapper = mount(ReviewQueueView, {
    global: { stubs: { ElDialog: ElDialogStub, ElDrawer: ElDrawerStub, ElSwitch: ElSwitchStub } },
  })
  await flushPromises()
  return wrapper
}

/** 点击列表 → ↑ 聚焦第一行：面板随焦点行自动加载 */
async function focusFirstRow(wrapper: VueWrapper) {
  const section = wrapper.find('section[tabindex="0"]')
  await section.trigger('focusin')
  await section.trigger('keydown', { key: 'ArrowDown' })
  await flushPromises()
  return section
}

describe('ReviewQueueView 键盘与快捷键', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    usePermissions().reset()
    mockedFetchAdminReviewQueue.mockResolvedValue({ items: [queueItem()], nextCursor: null })
    mockedFetchAdminReviewContext.mockResolvedValue(context())
    mockedFetchAdminReviewHistory.mockResolvedValue(history())
    mockedTakeDownPost.mockResolvedValue({ proposalId: 1 })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('行焦点：↑ 聚焦首行后上下文面板自动加载，不必先点行', async () => {
    wrapper = await mountView()

    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)

    await focusFirstRow(wrapper)

    expect(mockedFetchAdminReviewContext).toHaveBeenCalledWith('POST', 5)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(true)
  })

  it('快捷键只为有权限的动作渲染按键：无权限时一个键都不提示', async () => {
    wrapper = await mountView(['MODERATION_VIEW'])
    await focusFirstRow(wrapper)

    expect(wrapper.findAll('kbd')).toHaveLength(0)
  })

  it('快捷键按键随可用动作渲染，需 DISPOSITION_REVOKE 的动作不出现', async () => {
    wrapper = await mountView()
    await focusFirstRow(wrapper)

    const keys = wrapper.findAll('kbd').map((node) => node.text())
    expect(keys).toContain('1') // 通过
    expect(keys).toContain('2') // 驳回
    expect(keys).toContain('3') // 发起下架提案
    expect(keys).toContain('4') // 举报不成立
    expect(keys).not.toContain('8') // 恢复可见：需撤销权限
    expect(keys).not.toContain('9') // 恢复联系方式：需撤销权限
  })

  it('快捷键触发的动作仍先弹窗二次确认，不直接执行', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)

    await section.trigger('keydown', { key: '3' })
    await flushPromises()

    expect(wrapper.find('.el-dialog-stub').attributes('data-title')).toBe('发起下架提案')
    // 关键：只是打开确认弹窗，未提交
    expect(mockedTakeDownPost).not.toHaveBeenCalled()
  })

  it('未聚焦列表时数字键不触发动作（不抢全局输入）', async () => {
    wrapper = await mountView()
    const section = wrapper.find('section[tabindex="0"]')

    await section.trigger('keydown', { key: '3' })
    await flushPromises()

    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
    expect(mockedTakeDownPost).not.toHaveBeenCalled()
  })
})

describe('ReviewQueueView 筛选', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    usePermissions().reset()
    mockedFetchAdminReviewQueue.mockResolvedValue({ items: [queueItem()], nextCursor: null })
    mockedFetchAdminReviewContext.mockResolvedValue(context())
    mockedFetchAdminReviewHistory.mockResolvedValue(history())
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('渲染举报理由 / 目标类型 / 排序 / 只看超时 四个筛选控件', async () => {
    wrapper = await mountView()

    expect(wrapper.text()).toContain('举报理由')
    expect(wrapper.text()).toContain('目标类型')
    expect(wrapper.text()).toContain('排序')
    expect(wrapper.text()).toContain('只看超时')
  })

  it('切换「只看超时」把筛选作为查询参数下发后端并重查第一页', async () => {
    wrapper = await mountView()
    mockedFetchAdminReviewQueue.mockClear()

    await wrapper.find('.el-switch-stub').trigger('click')
    await flushPromises()

    expect(mockedFetchAdminReviewQueue).toHaveBeenCalledWith({
      cursor: null,
      reasons: [],
      targetType: null,
      overdueOnly: true,
      sort: 'REPORT_COUNT_DESC',
    })
  })

  it('筛选变化时同步重置行焦点与上下文面板', async () => {
    wrapper = await mountView()
    await focusFirstRow(wrapper)
    expect(wrapper.find('.el-drawer-stub').exists()).toBe(true)

    await wrapper.find('.el-switch-stub').trigger('click')
    await flushPromises()

    expect(wrapper.find('.el-drawer-stub').exists()).toBe(false)
  })
})

