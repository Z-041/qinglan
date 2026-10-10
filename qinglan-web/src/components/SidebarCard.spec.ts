import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import SidebarCard from './SidebarCard.vue'

describe('SidebarCard', () => {
  it('渲染标题与默认插槽内容', () => {
    const wrapper = mount(SidebarCard, {
      props: { title: '话题规则' },
      slots: { default: '<p>请勿发布联系方式</p>' },
    })

    expect(wrapper.text()).toContain('话题规则')
    expect(wrapper.text()).toContain('请勿发布联系方式')
  })

  it('卡片外壳沿用 ql-card 与 p-4 内边距', () => {
    const wrapper = mount(SidebarCard, { props: { title: '社区须知' } })

    expect(wrapper.classes()).toContain('ql-card')
    expect(wrapper.classes()).toContain('p-4')
  })
})
