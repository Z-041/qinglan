import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AnnouncementReader from '@shared/components/AnnouncementReader.vue'

type Overrides = Partial<{
  title: string
  content: string
  requireScrollToEnd: boolean
  requireConfirm: boolean
  confirmed: boolean
  scrolledToEnd: boolean
}>

function mountReader(overrides: Overrides = {}): VueWrapper {
  return mount(AnnouncementReader, {
    props: {
      title: '停水通知',
      content: '正文内容',
      requireScrollToEnd: false,
      requireConfirm: false,
      confirmed: false,
      scrolledToEnd: false,
      ...overrides,
    },
  })
}

describe('AnnouncementReader', () => {
  it('渲染标题与正文', () => {
    const wrapper = mountReader()

    expect(wrapper.text()).toContain('停水通知')
    expect(wrapper.text()).toContain('正文内容')
  })

  it('不渲染进度条：倒计时只由调用方以文字读秒（写在按钮文案里）呈现', () => {
    const wrapper = mountReader()

    // 原进度条是子树里唯一带内联 width 的元素；移除后不应再有任何内联样式
    expect(wrapper.find('[style]').exists()).toBe(false)
  })

  it('需勾选时渲染「我已了解」，勾选抛出 update:confirmed', async () => {
    const wrapper = mountReader({ requireConfirm: true })

    const checkbox = wrapper.get('input[type="checkbox"]')
    await checkbox.setValue(true)

    expect(wrapper.emitted('update:confirmed')).toEqual([[true]])
  })

  it('正文不足一屏时直接判定已滚到底并抛出事件，避免「需滚动」永远关不掉', async () => {
    const wrapper = mountReader({ requireScrollToEnd: true })
    await flushPromises()

    expect(wrapper.emitted('update:scrolledToEnd')).toEqual([[true]])
  })
})
