import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseSwitch from './BaseSwitch.vue'

describe('BaseSwitch', () => {
  it('用 role="switch" 表达语义，aria-checked 反映当前值', async () => {
    const wrapper = mount(BaseSwitch, { props: { modelValue: false, ariaLabel: '只看逾期' } })
    const button = wrapper.get('button')

    expect(button.attributes('role')).toBe('switch')
    expect(button.attributes('aria-label')).toBe('只看逾期')
    expect(button.attributes('aria-checked')).toBe('false')

    await button.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
    expect(wrapper.emitted('change')).toEqual([[true]])
  })

  it('disabled 时点击不切换', async () => {
    const wrapper = mount(BaseSwitch, { props: { modelValue: false, disabled: true } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('change')).toBeUndefined()
  })
})
