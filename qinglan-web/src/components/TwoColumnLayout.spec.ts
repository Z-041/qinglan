import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TwoColumnLayout from './TwoColumnLayout.vue'

describe('TwoColumnLayout', () => {
  it('主列渲染默认插槽，侧栏渲染具名插槽', () => {
    const wrapper = mount(TwoColumnLayout, {
      slots: { default: '<p>正文</p>', aside: '<p>侧栏内容</p>' },
    })

    expect(wrapper.text()).toContain('正文')
    expect(wrapper.text()).toContain('侧栏内容')
  })

  it('侧栏是页面里唯一的 aside，且始终带 sticky 与窄屏隐藏', () => {
    const wrapper = mount(TwoColumnLayout)

    const asides = wrapper.findAll('aside')
    expect(asides).toHaveLength(1)
    expect(asides[0].classes()).toContain('sticky')
    expect(asides[0].classes()).toContain('max-lg:hidden')
  })
})
