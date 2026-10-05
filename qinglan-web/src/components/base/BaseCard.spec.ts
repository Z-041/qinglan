import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseCard from './BaseCard.vue'

describe('BaseCard', () => {
  it('默认带卡片样式与内边距', () => {
    const wrapper = mount(BaseCard)

    expect(wrapper.classes()).toContain('ql-card')
    expect(wrapper.classes()).toContain('p-5')
    expect(wrapper.classes()).toContain('sm:p-6')
  })

  it('padded=false 时不加内边距', () => {
    const wrapper = mount(BaseCard, { props: { padded: false } })

    expect(wrapper.classes()).toContain('ql-card')
    expect(wrapper.classes()).not.toContain('p-5')
  })
})
