import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import WorkbenchList from './WorkbenchList.vue'

function mountList(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(WorkbenchList, {
    props: { hasMore: false, ...props },
    slots: { default: '<table class="stub-table"><tbody><tr><td>一行</td></tr></tbody></table>', ...slots },
  })
}

describe('WorkbenchList', () => {
  it('列表容器是可聚焦的 section，带tabindex 与焦点环', () => {
    const section = mountList().get('section')
    expect(section.attributes('tabindex')).toBe('0')
    expect(section.classes()).toEqual(
      expect.arrayContaining(['ql-card', 'outline-none', 'focus-visible:ring-2']),
    )
  })

  it('focusin / focusout / keydown 三个入口原样上抛，外壳不接管键盘逻辑', async () => {
    const wrapper = mountList()
    const section = wrapper.get('section')

    await section.trigger('focusin')
    await section.trigger('focusout')
    await section.trigger('keydown', { key: '1' })

    expect(wrapper.emitted('focusin')).toHaveLength(1)
    expect(wrapper.emitted('focusout')).toHaveLength(1)
    expect(wrapper.emitted('keydown')).toHaveLength(1)
  })

  it('「有新内容」提示条默认不渲染，为真时点击抛 apply-now', async () => {
    expect(mountList().find('button').exists()).toBe(false)

    const wrapper = mountList({ hasNewContent: true })
    const hint = wrapper.findAll('button').find((b) => b.text().includes('列表有新内容'))!
    expect(hint.classes()).toEqual(expect.arrayContaining(['border-warning', 'bg-warning-soft']))

    await hint.trigger('click')
    expect(wrapper.emitted('apply-now')).toHaveLength(1)
  })

  it('快捷键提示只渲染已过滤的绑定，点击 kbd 不产生副作用', () => {
    const wrapper = mountList({ shortcuts: [{ key: '1', label: '通过' }] })
    expect(wrapper.findAll('kbd').map((n) => n.text())).toEqual(['1'])
    expect(wrapper.text()).toContain('通过')
  })

  it('默认插槽（表格）与加载更多同处容器内：hasMore 为真才渲染加载更多', () => {
    expect(mountList().find('.stub-table').exists()).toBe(true)
    expect(mountList().findAll('button')).toHaveLength(0)

    const wrapper = mountList({ hasMore: true })
    const loadMore = wrapper.findAll('button').find((b) => b.text() === '加载更多')!
    expect(loadMore.exists()).toBe(true)
    expect(wrapper.find('section').find('.stub-table').exists()).toBe(true)
  })

  it('加载更多点击抛 load', async () => {
    const wrapper = mountList({ hasMore: true })
    await wrapper.findAll('button').find((b) => b.text() === '加载更多')!.trigger('click')
    expect(wrapper.emitted('load')).toHaveLength(1)
  })
})
