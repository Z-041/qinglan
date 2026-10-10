import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import LoadMoreButton from './LoadMoreButton.vue'

describe('LoadMoreButton', () => {
  it('hasMore 为 false 时不渲染', () => {
    expect(mount(LoadMoreButton, { props: { hasMore: false } }).find('div').exists()).toBe(false)
  })

  it('点击按钮抛出 load 事件', async () => {
    const wrapper = mount(LoadMoreButton, { props: { hasMore: true } })
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('load')).toHaveLength(1)
  })

  it('loading 时按钮进入转圈态（防连点）', () => {
    const wrapper = mount(LoadMoreButton, { props: { hasMore: true, loading: true } })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[aria-hidden="true"].animate-spin').exists()).toBe(true)
  })

  it('hint 非空时在按钮上方渲染提示，disabled 透传给按钮', () => {
    const wrapper = mount(LoadMoreButton, {
      props: { hasMore: true, hint: '请先查询', disabled: true },
    })
    expect(wrapper.text()).toContain('请先查询')
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('plain 为垂直居中容器，table 为分隔行容器', () => {
    const plain = mount(LoadMoreButton, { props: { hasMore: true } }).get('div')
    expect(plain.classes()).toEqual(expect.arrayContaining(['flex', 'flex-col', 'items-center']))

    const table = mount(LoadMoreButton, { props: { hasMore: true, variant: 'table' } }).get('div')
    expect(table.classes()).toEqual(expect.arrayContaining(['border-t', 'border-line', 'text-center']))
    expect(table.classes()).not.toContain('flex')
  })
})
