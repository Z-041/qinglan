import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CommentComposer from './CommentComposer.vue'
import type { Comment } from '@/types/comment'

function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 1,
    content: 'x',
    anonymousCode: '梧桐#1',
    likeCount: 0,
    liked: false,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    replies: [],
    ...overrides,
  }
}

function mountComposer(props: Record<string, unknown> = {}) {
  return mount(CommentComposer, {
    props: { modelValue: '', authenticated: true, ...props },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
  })
}

describe('CommentComposer', () => {
  it('未认证时给出登录引导，不渲染输入框', () => {
    const wrapper = mountComposer({ authenticated: false })
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.text()).toContain('可参与评论')
  })

  it('空内容时提交按钮禁用；输入后可用', async () => {
    const wrapper = mountComposer()
    const button = wrapper.get('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()

    await wrapper.setProps({ modelValue: '你好' })
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('输入抛出 update:modelValue，提交抛出 submit', async () => {
    const wrapper = mountComposer()
    await wrapper.get('textarea').setValue('新评论')
    expect(wrapper.emitted('update:modelValue')).toEqual([['新评论']])

    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('回复态展示回复对象并抛出 cancel-reply', async () => {
    const wrapper = mountComposer({ replyTo: comment() })
    expect(wrapper.text()).toContain('正在回复 梧桐#1')

    await wrapper.findAll('button').find((b) => b.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('cancel-reply')).toHaveLength(1)
  })

  it('提交中按钮进入在途态', () => {
    const wrapper = mountComposer({ modelValue: '你好', submitting: true })
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
