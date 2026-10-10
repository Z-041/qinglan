import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TextButton from './TextButton.vue'

describe('TextButton', () => {
  it('渲染为 type=button，点击上抛事件', async () => {
    const wrapper = mount(TextButton, { slots: { default: '回复' } })

    const button = wrapper.get('button')
    expect(button.attributes('type')).toBe('button')
    expect(button.text()).toBe('回复')

    await button.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('tone 决定 hover 变色：默认主色，danger 为危险色', () => {
    expect(mount(TextButton).get('button').classes()).toContain('hover:text-primary-deep')
    expect(mount(TextButton, { props: { tone: 'danger' } }).get('button').classes()).toContain('hover:text-danger')
  })

  it('disabled 时渲染 disabled 属性并置灰', () => {
    const button = mount(TextButton, { props: { disabled: true } }).get('button')

    expect(button.attributes('disabled')).toBeDefined()
    expect(button.classes()).toContain('disabled:opacity-60')
  })

  it('aria-label 与额外定位类经属性透传到根 button', () => {
    const wrapper = mount(TextButton, {
      attrs: { 'aria-label': '删除我的评论', class: 'shrink-0' },
    })

    const button = wrapper.get('button')
    expect(button.attributes('aria-label')).toBe('删除我的评论')
    expect(button.classes()).toContain('shrink-0')
  })
})
