import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseCheckbox from './BaseCheckbox.vue'

describe('BaseCheckbox', () => {
  it('渲染文案，支持 v-model 双向绑定', async () => {
    const wrapper = mount(BaseCheckbox, { props: { modelValue: false, label: '只看我发起的' } })
    expect(wrapper.text()).toContain('只看我发起的')

    await wrapper.get('input').setValue(true)
    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('disabled 时输入不可用', () => {
    const wrapper = mount(BaseCheckbox, { props: { modelValue: false, label: 'x', disabled: true } })
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  })

  it('无文案时可用 ariaLabel 提供可访问名', () => {
    const wrapper = mount(BaseCheckbox, { props: { modelValue: false, ariaLabel: '全选' } })
    expect(wrapper.get('input').attributes('aria-label')).toBe('全选')
  })
})
