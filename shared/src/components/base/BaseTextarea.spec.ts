import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseTextarea from './BaseTextarea.vue'

describe('BaseTextarea', () => {
  it('输入时向外抛出 update:modelValue', async () => {
    const wrapper = mount(BaseTextarea)

    await wrapper.get('textarea').setValue('hello')
    expect(wrapper.emitted('update:modelValue')).toEqual([['hello']])
  })

  it('受控值变化时同步到原生输入框', async () => {
    const wrapper = mount(BaseTextarea, { props: { modelValue: '' } })

    await wrapper.setProps({ modelValue: 'abc' })
    expect(wrapper.get('textarea').element.value).toBe('abc')
  })

  it('invalid 时追加危险边框并标记 aria-invalid', () => {
    const wrapper = mount(BaseTextarea, { props: { invalid: true } })
    const textarea = wrapper.get('textarea')

    expect(textarea.classes()).toContain('!border-danger')
    expect(textarea.attributes('aria-invalid')).toBe('true')
  })
})
