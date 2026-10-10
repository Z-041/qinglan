import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RelativeTime from './RelativeTime.vue'

const ISO = '2026-10-01T08:30:00Z'

describe('RelativeTime', () => {
  it('默认 relative：time 元素带 datetime，悬停 title 为完整绝对时间，正文为相对时间', () => {
    const wrapper = mount(RelativeTime, { props: { iso: ISO } })
    const time = wrapper.get('time')

    expect(time.attributes('datetime')).toBe(ISO)
    expect(time.attributes('title')).toMatch(/2026/)
    expect(time.text()).toBeTruthy()
  })

  it('absolute-short：正文为短绝对时间而非相对文案，title 仍给完整时刻', () => {
    const relative = mount(RelativeTime, { props: { iso: ISO } }).text()
    const short = mount(RelativeTime, { props: { iso: ISO, variant: 'absolute-short' } })

    expect(short.get('time').attributes('datetime')).toBe(ISO)
    expect(short.text()).toMatch(/2026/)
    // 短绝对格式是日期串，必然包含年；相对格式（如「x 天前」）不含，两者可区分
    expect(short.text()).not.toBe(relative)
  })

  it('class 透传到 time 元素，供调用方融入表格 / 弱化文案', () => {
    const wrapper = mount(RelativeTime, { props: { iso: ISO }, attrs: { class: 'text-xs text-muted' } })
    expect(wrapper.get('time').classes()).toEqual(expect.arrayContaining(['text-xs', 'text-muted']))
  })
})
