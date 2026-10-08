import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseCard from './BaseCard.vue'

describe('BaseCard', () => {
  it('默认带卡片样式与内边距', () => {
    const wrapper = mount(BaseCard)

    expect(wrapper.classes()).toContain('ql-card')
    expect(wrapper.classes()).toContain('p-6')
  })

  it('padded=false 时不加内边距', () => {
    const wrapper = mount(BaseCard, { props: { padded: false } })

    expect(wrapper.classes()).toContain('ql-card')
    expect(wrapper.classes()).not.toContain('p-6')
  })

  it('窄屏收窄内边距：PC 基准即 p-6，窄屏用 max-sm 覆盖为 p-5', () => {
    // 断言的是「基线 + 收窄」这层结构，而不是某个断点类本身被原样保留：
    // 改写为 max-sm: 口径时（AGENTS.md 第七章）这条用例不应变成阻碍
    const wrapper = mount(BaseCard)
    const paddingClasses = wrapper.classes().filter((name) => name.includes('p-6') || name.includes('p-5'))

    expect(paddingClasses).toEqual(expect.arrayContaining(['p-6', 'max-sm:p-5']))
  })
})
