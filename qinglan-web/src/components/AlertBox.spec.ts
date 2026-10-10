import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AlertBox from './AlertBox.vue'

describe('AlertBox', () => {
  it('默认危险档：浅底 + 同色系深字，渲染纯文本', () => {
    const box = mount(AlertBox, { slots: { default: '审核未通过' } }).find('p')

    expect(box.text()).toBe('审核未通过')
    expect(box.classes()).toContain('bg-danger-soft')
    expect(box.classes()).toContain('text-danger-deep')
  })

  it('tone 切换底色与文字配对：warning / primary', () => {
    const warning = mount(AlertBox, { props: { tone: 'warning' } }).find('p')
    expect(warning.classes()).toEqual(expect.arrayContaining(['bg-warning-soft', 'text-warning']))

    const primary = mount(AlertBox, { props: { tone: 'primary' } }).find('p')
    expect(primary.classes()).toEqual(
      expect.arrayContaining(['bg-primary-tint', 'text-primary-deep']),
    )
  })

  it('额外间距/断点类经属性透传，基调不变', () => {
    const box = mount(AlertBox, {
      props: { tone: 'warning' },
      attrs: { class: 'mt-3 max-lg:block' },
    }).find('p')

    expect(box.classes()).toEqual(expect.arrayContaining(['mt-3', 'max-lg:block', 'bg-warning-soft']))
  })
})
