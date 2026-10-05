import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseButton from './BaseButton.vue'

describe('BaseButton', () => {
  it('默认渲染主色中号按钮与插槽内容', () => {
    const wrapper = mount(BaseButton, { slots: { default: '提交' } })

    expect(wrapper.text()).toContain('提交')
    expect(wrapper.classes()).toContain('bg-primary-deep')
    expect(wrapper.classes()).toContain('ql-btn-md')
  })

  it('loading 时禁用按钮并展示加载指示器', () => {
    const wrapper = mount(BaseButton, { props: { loading: true } })

    expect(wrapper.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(true)
  })

  it('ghost + block 使用次要样式并占满整行', () => {
    const wrapper = mount(BaseButton, { props: { variant: 'ghost', block: true } })

    expect(wrapper.classes()).toContain('w-full')
    expect(wrapper.classes()).toContain('bg-transparent')
  })

  it('点击时向外抛出 click 事件', async () => {
    const wrapper = mount(BaseButton, { props: { htmlType: 'submit' } })

    expect(wrapper.attributes('type')).toBe('submit')
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
