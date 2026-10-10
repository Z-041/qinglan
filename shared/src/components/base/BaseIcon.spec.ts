import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BaseIcon from './BaseIcon.vue'
import BaseSpinner from './BaseSpinner.vue'

describe('BaseIcon', () => {
  it('按 name 渲染对应 path，未知图形不会被渲染（类型外取值编译期即挡）', () => {
    const heart = mount(BaseIcon, { props: { name: 'heart' } })
    expect(heart.findAll('path')).toHaveLength(1)
    expect(heart.html()).toContain('M12 20.3')

    expect(mount(BaseIcon, { props: { name: 'bell' } }).findAll('path')).toHaveLength(2)
    expect(mount(BaseIcon, { props: { name: 'comment' } }).findAll('path')).toHaveLength(1)
    // plus 的两条线段合并在同一个 path 的 d 里
    expect(mount(BaseIcon, { props: { name: 'plus' } }).findAll('path')).toHaveLength(1)
  })

  it('heart 默认空心，filled 时实心（currentColor）；其余图标恒空心', () => {
    const outline = mount(BaseIcon, { props: { name: 'heart' } })
    expect(outline.get('svg').attributes('fill')).toBe('none')

    const filled = mount(BaseIcon, { props: { name: 'heart', filled: true } })
    expect(filled.get('svg').attributes('fill')).toBe('currentColor')

    const bell = mount(BaseIcon, { props: { name: 'bell', filled: true } })
    expect(bell.get('svg').attributes('fill')).toBe('none')
  })

  it('对屏幕阅读器隐藏、不参与焦点，语义留给所在按钮的 aria-label', () => {
    const icon = mount(BaseIcon, { props: { name: 'heart' }, attrs: { class: 'size-4' } })
    expect(icon.get('svg').attributes('aria-hidden')).toBe('true')
    expect(icon.get('svg').attributes('focusable')).toBe('false')
    expect(icon.classes()).toContain('size-4')
  })
})

describe('BaseSpinner', () => {
  it('默认 size-4，可由 sizeClass 覆盖，带旋转动画且对读屏隐藏', () => {
    const def = mount(BaseSpinner)
    expect(def.classes()).toEqual(expect.arrayContaining(['size-4', 'animate-spin']))
    expect(def.attributes('aria-hidden')).toBe('true')

    const small = mount(BaseSpinner, { props: { sizeClass: 'size-3.5' } })
    expect(small.classes()).toContain('size-3.5')
    expect(small.classes()).not.toContain('size-4')
  })
})
