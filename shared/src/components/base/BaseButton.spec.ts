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

  it('xs 尺寸用 ql-btn-xs，表格行内动作专用', () => {
    const wrapper = mount(BaseButton, { props: { size: 'xs' } })
    expect(wrapper.classes()).toContain('ql-btn-xs')
    expect(wrapper.classes()).not.toContain('ql-btn-sm')
  })

  it('ghost-danger 为描边危险态：透明底 + 危险色悬停，区别于实心 danger', () => {
    const ghostDanger = mount(BaseButton, { props: { variant: 'ghost-danger' } })
    expect(ghostDanger.classes()).toContain('bg-transparent')
    expect(ghostDanger.classes()).toContain('hover:border-danger')

    expect(mount(BaseButton, { props: { variant: 'danger' } }).classes()).toContain('bg-danger')
  })

  it('as 渲染为链接元素：不出 type / disabled，样式与内容照旧', () => {
    const wrapper = mount(BaseButton, {
      props: { as: 'a', size: 'sm' },
      attrs: { href: '/publish' },
    })
    const root = wrapper.get('a')

    expect(root.classes()).toContain('ql-btn')
    expect(root.classes()).toContain('ql-btn-sm')
    expect(root.attributes('href')).toBe('/publish')
    // 链接不接受 button 的 type / disabled
    expect(root.attributes('type')).toBeUndefined()
    expect(root.attributes('disabled')).toBeUndefined()
  })

  it('默认仍渲染 button 且带 type', () => {
    expect(mount(BaseButton).get('button').attributes('type')).toBe('button')
  })

  it('点击时向外抛出 click 事件', async () => {
    const wrapper = mount(BaseButton, { props: { htmlType: 'submit' } })

    expect(wrapper.attributes('type')).toBe('submit')
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })
})
