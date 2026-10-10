import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatePanel from './StatePanel.vue'

function mountPanel(state: 'loading' | 'failed' | 'empty' | 'ready', props: Record<string, unknown> = {}) {
  return mount(StatePanel, {
    props: { state, ...props },
    slots: { default: '<p>正常内容</p>', 'empty-action': '<a>去发布</a>' },
  })
}

describe('StatePanel', () => {
  it('loading：只显示加载文案，不显示重试与内容', () => {
    const wrapper = mountPanel('loading')
    expect(wrapper.text()).toContain('加载中…')
    expect(wrapper.text()).not.toContain('正常内容')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('failed：显示失败标题与重试按钮，点击抛 retry，不渲染内容', async () => {
    const wrapper = mountPanel('failed', { failedTitle: '内容没能加载出来' })
    expect(wrapper.text()).toContain('内容没能加载出来')
    expect(wrapper.text()).not.toContain('正常内容')

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('empty：显示标题/描述与 empty-action 插槽', () => {
    const wrapper = mountPanel('empty', { emptyTitle: '还没有消息', emptyDescription: '刷新看看' })
    expect(wrapper.text()).toContain('还没有消息')
    expect(wrapper.text()).toContain('刷新看看')
    expect(wrapper.find('a').text()).toBe('去发布')
  })

  it('empty 无描述无 action 时不渲染描述段与包装', () => {
    const wrapper = mountPanel('empty', { emptyTitle: '暂无' })
    expect(wrapper.findAll('p')).toHaveLength(1)
  })

  it('ready：渲染默认插槽，不出现任何状态盒', () => {
    const wrapper = mountPanel('ready')
    expect(wrapper.text()).toContain('正常内容')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('顶部间距可被 topSpaceClass 覆盖（gap 容器内去重边距）', () => {
    const wrapper = mountPanel('empty', { topSpaceClass: 'mt-0' })
    expect(wrapper.get('div').classes()).toContain('mt-0')
    expect(wrapper.get('div').classes()).not.toContain('mt-6')
  })
})
