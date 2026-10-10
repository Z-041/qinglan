import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AnnouncementReader from '@shared/components/AnnouncementReader.vue'

type Overrides = Partial<{
  title: string
  content: string
  minReadSeconds: number
  remainingSeconds: number
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
      minReadSeconds: 10,
      remainingSeconds: 10,
      requireScrollToEnd: false,
      requireConfirm: false,
      confirmed: false,
      scrolledToEnd: false,
      ...overrides,
    },
  })
}

describe('AnnouncementReader', () => {
  it('渲染标题与正文，进度条按剩余秒数推进', () => {
    // minRead 10、剩 4 → 60%
    const wrapper = mountReader({ remainingSeconds: 4 })

    expect(wrapper.text()).toContain('停水通知')
    expect(wrapper.text()).toContain('正文内容')
    expect(wrapper.find('[style*="width"]').attributes('style')).toContain('60%')
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
