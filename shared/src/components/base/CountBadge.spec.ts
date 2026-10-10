import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CountBadge from './CountBadge.vue'

describe('CountBadge', () => {
  it('count <= 0 时不渲染', () => {
    expect(mount(CountBadge, { props: { count: 0 } }).find('span').exists()).toBe(false)
    expect(mount(CountBadge, { props: { count: -3 } }).find('span').exists()).toBe(false)
  })

  it('原样显示未超上限的计数', () => {
    expect(mount(CountBadge, { props: { count: 12 } }).text()).toBe('12')
    expect(mount(CountBadge, { props: { count: 99 } }).text()).toBe('99')
  })

  it('超上限显示 99+，自定义 max 同理', () => {
    expect(mount(CountBadge, { props: { count: 100 } }).text()).toBe('99+')
    expect(mount(CountBadge, { props: { count: 20, max: 19 } }).text()).toBe('19+')
  })

  it('装饰性角标对读屏隐藏，尺寸档位映射到既有两套规格', () => {
    const sm = mount(CountBadge, { props: { count: 1 } })
    expect(sm.find('span').attributes('aria-hidden')).toBe('true')
    expect(sm.find('span').classes()).toContain('h-4')

    const md = mount(CountBadge, { props: { count: 1, size: 'md' } })
    expect(md.find('span').classes()).toContain('h-[18px]')
    expect(md.find('span').classes()).not.toContain('h-4')
  })
})
