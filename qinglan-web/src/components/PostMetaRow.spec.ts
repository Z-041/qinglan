import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PostMetaRow from './PostMetaRow.vue'

const ISO = '2026-10-06T00:00:00Z'

describe('PostMetaRow', () => {
  it('渲染话题、代号与相对时间；非本人无「我」标签', () => {
    const wrapper = mount(PostMetaRow, {
      props: { anonymousCode: '银杏#7', createdAt: ISO, topicName: '问答' },
    })
    expect(wrapper.text()).toContain('问答')
    expect(wrapper.text()).toContain('银杏#7')
    expect(wrapper.text()).not.toContain('我')
    expect(wrapper.find('time').attributes('datetime')).toBe(ISO)
  })

  it('mine 为真时渲染「我」标签', () => {
    const wrapper = mount(PostMetaRow, { props: { anonymousCode: 'x', createdAt: ISO, mine: true } })
    expect(wrapper.text()).toContain('我')
  })

  it('不传话题名时不渲染话题标签', () => {
    const wrapper = mount(PostMetaRow, { props: { anonymousCode: 'x', createdAt: ISO } })
    expect(wrapper.findAll('span.ql-tag')).toHaveLength(0)
  })

  it('#leading 插槽内容排在首位（供「我的帖子」放状态标签）', () => {
    const wrapper = mount(PostMetaRow, {
      props: { anonymousCode: 'x', createdAt: ISO, topicName: '问答' },
      slots: { leading: '<span class="status-tag">待审核</span>' },
    })
    const spans = wrapper.findAll('span')
    expect(spans[0].classes()).toContain('status-tag')
  })
})
