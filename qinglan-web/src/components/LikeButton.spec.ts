import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LikeButton from './LikeButton.vue'

describe('LikeButton', () => {
  it('渲染计数与完整 aria-label，未赞为空心、取消语义跟随 liked', () => {
    const wrapper = mount(LikeButton, { props: { liked: false, count: 12, label: '点赞' } })
    const button = wrapper.get('button')

    expect(button.text()).toBe('12')
    expect(button.attributes('aria-pressed')).toBe('false')
    expect(button.attributes('aria-label')).toBe('点赞')
    expect(button.find('svg').attributes('fill')).toBe('none')
  })

  it('已赞时 aria-pressed 为真、心形实心、文案为取消点赞', () => {
    const wrapper = mount(LikeButton, { props: { liked: true, count: 13, label: '取消点赞' } })
    expect(wrapper.get('button').attributes('aria-pressed')).toBe('true')
    expect(wrapper.find('svg').attributes('fill')).toBe('currentColor')
  })

  it('点击抛出 toggle', async () => {
    const wrapper = mount(LikeButton, { props: { liked: false, count: 0, label: '点赞' } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
  })

  it('sm 尺寸用 size-3.5，md 默认 size-4', () => {
    const sm = mount(LikeButton, { props: { liked: false, count: 0, label: '点赞', size: 'sm' } })
    expect(sm.find('svg').classes()).toContain('size-3.5')

    const md = mount(LikeButton, { props: { liked: false, count: 0, label: '点赞' } })
    expect(md.find('svg').classes()).toContain('size-4')
  })
})
