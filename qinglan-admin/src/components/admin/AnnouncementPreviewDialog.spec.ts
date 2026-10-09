import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import AnnouncementPreviewDialog from '@/components/admin/AnnouncementPreviewDialog.vue'

/** el-dialog 走 Teleport 且懒渲染，单测里换成仅按 modelValue 渲染插槽的桩 */
const ElDialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
}

type Overrides = Partial<{
  open: boolean
  title: string
  content: string
  minReadSeconds: number
  requireScrollToEnd: boolean
  requireConfirm: boolean
}>

function mountDialog(overrides: Overrides = {}): VueWrapper {
  return mount(AnnouncementPreviewDialog, {
    props: {
      open: true,
      title: '停水通知',
      content: '今晚 22:00 起停水，请提前储水。',
      minReadSeconds: 3,
      requireScrollToEnd: false,
      requireConfirm: false,
      ...overrides,
    },
    global: { stubs: { ElDialog: ElDialogStub } },
  })
}

describe('AnnouncementPreviewDialog', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('渲染学生端同款标题与正文', async () => {
    const wrapper = mountDialog()
    await nextTick()

    expect(wrapper.text()).toContain('停水通知')
    expect(wrapper.text()).toContain('今晚 22:00 起停水，请提前储水。')
  })

  it('倒计时未满时关闭按钮禁用并显示倒计时', async () => {
    const wrapper = mountDialog({ minReadSeconds: 3 })
    await nextTick()

    // Reader 内没有别的按钮，整棵子树里唯一的按钮就是预览的关闭按钮
    const close = wrapper.get('button')
    expect((close.element as HTMLButtonElement).disabled).toBe(true)
    expect(close.text()).toBe('3s 后可关闭')
  })

  it('倒计时结束后关闭按钮可用，点击后抛出 close', async () => {
    const wrapper = mountDialog({ minReadSeconds: 3 })
    await nextTick()

    vi.advanceTimersByTime(3000)
    await nextTick()

    const close = wrapper.get('button')
    expect(close.text()).toBe('我知道了')
    expect((close.element as HTMLButtonElement).disabled).toBe(false)

    await close.trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('需勾选「已了解」时，未勾选不可关闭，勾选后放行', async () => {
    const wrapper = mountDialog({ minReadSeconds: 0, requireConfirm: true })
    await nextTick()

    const close = wrapper.get('button')
    expect((close.element as HTMLButtonElement).disabled).toBe(true)

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await nextTick()

    expect((close.element as HTMLButtonElement).disabled).toBe(false)
  })
})
