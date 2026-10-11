import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseTabs from './BaseTabs.vue'

const tabs = [
  { name: 'context', label: '举报概况' },
  { name: 'author', label: '作者动态' },
]

describe('BaseTabs', () => {
  it('渲染 tablist 与页签，选中态由 aria-selected 表达', () => {
    const wrapper = mount(BaseTabs, { props: { modelValue: 'context', tabs, ariaLabel: '审核上下文' } })

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe('审核上下文')
    const items = wrapper.findAll('[role="tab"]')
    expect(items.map((i) => i.text())).toEqual(['举报概况', '作者动态'])
    expect(items[0].attributes('aria-selected')).toBe('true')
    expect(items[1].attributes('aria-selected')).toBe('false')
  })

  it('点击页签抛 update:modelValue', async () => {
    const wrapper = mount(BaseTabs, { props: { modelValue: 'context', tabs } })
    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([['author']])
  })
})
