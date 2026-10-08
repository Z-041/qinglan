import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FormField from './FormField.vue'

describe('FormField', () => {
  it('渲染标签并透传插槽内容', () => {
    const wrapper = mount(FormField, { props: { label: '邮箱' }, slots: { default: '<input />' } })

    expect(wrapper.text()).toContain('邮箱')
    expect(wrapper.find('input').exists()).toBe(true)
  })

  it('同时存在错误与提示时优先展示错误并带 role=alert', () => {
    const wrapper = mount(FormField, {
      props: { label: '邮箱', error: '邮箱格式错误', hint: '请输入校园邮箱' },
    })

    expect(wrapper.text()).toContain('邮箱格式错误')
    expect(wrapper.text()).not.toContain('请输入校园邮箱')
    expect(wrapper.get('[role="alert"]').text()).toBe('邮箱格式错误')
  })

  it('无错误时展示提示文案', () => {
    const wrapper = mount(FormField, { props: { label: '邮箱', hint: '请输入校园邮箱' } })

    expect(wrapper.text()).toContain('请输入校园邮箱')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})
