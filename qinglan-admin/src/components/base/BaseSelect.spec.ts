import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseSelect from './BaseSelect.vue'

const options = [
  { value: '', label: '不限' },
  { value: 'ACTIVE', label: '正常' },
]

describe('BaseSelect', () => {
  it('渲染原生 select 与全部 option（含「不限」占位项）', () => {
    const wrapper = mount(BaseSelect, { props: { modelValue: '', options, ariaLabel: '状态' } })

    const select = wrapper.get('select')
    expect(select.attributes('aria-label')).toBe('状态')
    expect(wrapper.findAll('option').map((o) => o.text())).toEqual(['不限', '正常'])
  })

  it('change 抛 update:modelValue', async () => {
    const wrapper = mount(BaseSelect, { props: { modelValue: '', options } })
    await wrapper.get('select').setValue('ACTIVE')
    expect(wrapper.emitted('update:modelValue')).toEqual([['ACTIVE']])
  })

  it('invalid 时挂危险描边与 aria-invalid', () => {
    const wrapper = mount(BaseSelect, { props: { modelValue: '', options, invalid: true } })
    expect(wrapper.get('select').classes()).toContain('!border-danger')
    expect(wrapper.get('select').attributes('aria-invalid')).toBe('true')
  })
})
