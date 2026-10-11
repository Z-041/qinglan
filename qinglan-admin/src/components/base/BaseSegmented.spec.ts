import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseSegmented from './BaseSegmented.vue'

const options = [
  { value: '7d', label: '近 7 日' },
  { value: '30d', label: '近 30 日' },
]

describe('BaseSegmented', () => {
  it('渲染为 radiogroup，选中项 aria-checked=true', () => {
    const wrapper = mount(BaseSegmented, {
      props: { modelValue: '7d', options, label: '时间范围' },
    })

    expect(wrapper.get('[role="radiogroup"]').attributes('aria-label')).toBe('时间范围')
    const radios = wrapper.findAll('[role="radio"]')
    expect(radios.map((r) => r.text())).toEqual(['近 7 日', '近 30 日'])
    expect(radios[0].attributes('aria-checked')).toBe('true')
    expect(radios[1].attributes('aria-checked')).toBe('false')
  })

  it('点击某段抛 update:modelValue', async () => {
    const wrapper = mount(BaseSegmented, { props: { modelValue: '7d', options } })
    await wrapper.findAll('[role="radio"]')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['30d']])
  })
})
