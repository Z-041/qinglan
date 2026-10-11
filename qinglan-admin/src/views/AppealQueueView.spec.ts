import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchAdminMe } from '@/api/admin'
import { acceptAppeal, fetchAdminAppeals, rejectAppeal } from '@/api/review'
import AppealReviewDialog from '@/components/admin/AppealReviewDialog.vue'
import { usePermissions } from '@/composables/usePermissions'
import AppealQueueView from '@/views/AppealQueueView.vue'
import type { AdminPermission } from '@/types/permission'
import type { AdminAppealItem } from '@/types/review'

vi.mock('@/api/admin', () => ({ fetchAdminMe: vi.fn() }))
vi.mock('@/api/review', () => ({
  fetchAdminAppeals: vi.fn(),
  acceptAppeal: vi.fn(),
  rejectAppeal: vi.fn(),
}))

const mockedFetchAdminMe = vi.mocked(fetchAdminMe)
const mockedFetchAdminAppeals = vi.mocked(fetchAdminAppeals)
const mockedAcceptAppeal = vi.mocked(acceptAppeal)
const mockedRejectAppeal = vi.mocked(rejectAppeal)

function appeal(overrides: Partial<AdminAppealItem> = {}): AdminAppealItem {
  return {
    appealId: 11,
    targetType: 'POST',
    targetId: 5,
    preview: '被下架正文',
    targetStatus: 'TAKEN_DOWN',
    takenDownReason: 'ABUSE',
    appealReason: '我认为不违规',
    appealedAt: '2026-10-02T00:00:00Z',
    ...overrides,
  }
}

async function mountView(permissions: AdminPermission[] = ['MODERATION_VIEW', 'APPEAL_REVIEW']): Promise<VueWrapper> {
  mockedFetchAdminMe.mockResolvedValue({ userId: 1, role: 'REVIEWER', permissions })
  await usePermissions().load()

  const wrapper = mount(AppealQueueView)
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

describe('AppealQueueView 键盘与快捷键', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    vi.clearAllMocks()
    usePermissions().reset()
    mockedFetchAdminAppeals.mockResolvedValue({ items: [appeal()], nextCursor: null })
    mockedAcceptAppeal.mockResolvedValue(undefined)
    mockedRejectAppeal.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('快捷键按键随本页动作集渲染：只有 1 通过申诉 / 2 维持原判', async () => {
    wrapper = await mountView()
    await focusFirstRow(wrapper)

    expect(wrapper.findAll('kbd').map((node) => node.text())).toEqual(['1', '2'])
  })

  it('无 APPEAL_REVIEW 权限时不渲染按键，按键也不触发', async () => {
    wrapper = await mountView(['MODERATION_VIEW'])
    const section = await focusFirstRow(wrapper)

    expect(wrapper.findAll('kbd')).toHaveLength(0)

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('「1 通过申诉」先打开弹窗并预置「通过」，不直接执行', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    const dialog = wrapper.findComponent(AppealReviewDialog)
    expect(dialog.exists()).toBe(true)
    expect(dialog.props('initialApproved')).toBe(true)
    expect(mockedAcceptAppeal).not.toHaveBeenCalled()
  })

  it('「2 维持原判」打开弹窗并预置「维持原判」结论', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)

    await section.trigger('keydown', { key: '2' })
    await flushPromises()

    expect(wrapper.findComponent(AppealReviewDialog).props('initialApproved')).toBe(false)
    expect(mockedRejectAppeal).not.toHaveBeenCalled()
  })

  it('未聚焦列表时数字键不触发（不抢全局输入）', async () => {
    wrapper = await mountView()
    const section = wrapper.find('section')

    await section.trigger('keydown', { key: '1' })
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('弹窗提交后按结论调用接口并刷新队列（连续处置：焦点留待下一条）', async () => {
    wrapper = await mountView()
    const section = await focusFirstRow(wrapper)
    await section.trigger('keydown', { key: '2' })
    await flushPromises()

    wrapper.findComponent(AppealReviewDialog).vm.$emit('submit', false, '依据不足')
    await flushPromises()

    expect(mockedRejectAppeal).toHaveBeenCalledWith(11, { reason: '依据不足' })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(mockedFetchAdminAppeals).toHaveBeenCalledTimes(2)
  })
})
