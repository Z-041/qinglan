import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseInput from './BaseInput.vue'

describe('BaseInput', () => {
  it('输入时向外抛出 update:modelValue', async () => {
    const wrapper = mount(BaseInput)

    await wrapper.get('input').setValue('hello')
    expect(wrapper.emitted('update:modelValue')).toEqual([['hello']])
  })

  it('受控值变化时同步到原生输入框', async () => {
    const wrapper = mount(BaseInput, { props: { modelValue: '' } })

    await wrapper.setProps({ modelValue: 'abc' })
    expect(wrapper.get('input').element.value).toBe('abc')
  })

  it('invalid 时追加危险边框并标记 aria-invalid', () => {
    const wrapper = mount(BaseInput, { props: { invalid: true } })
    const input = wrapper.get('input')

    expect(input.classes()).toContain('!border-danger')
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('回车时抛出 enter 事件', async () => {
    const wrapper = mount(BaseInput)

    await wrapper.get('input').trigger('keyup.enter')
    expect(wrapper.emitted('enter')).toHaveLength(1)
  })
})
