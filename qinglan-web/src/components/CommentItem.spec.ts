import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CommentItem from './CommentItem.vue'
import type { Comment } from '@/types/comment'

function comment(overrides: Partial<Comment> = {}): Comment {
  return {
    id: 3,
    content: '这条还有吗',
    anonymousCode: '梧桐#3',
    likeCount: 0,
    liked: false,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    replies: [],
    ...overrides,
  }
}

function mountItem(data: Comment, reportable = true) {
  return mount(CommentItem, { props: { comment: data, reportable } })
}

describe('CommentItem', () => {
  it('未认证访问者看不到举报入口', () => {
    const wrapper = mountItem(comment(), false)

    expect(wrapper.text()).not.toContain('举报')
  })

  it('已认证时他人评论可举报，点击抛出该评论', async () => {
    const wrapper = mountItem(comment())
    const button = wrapper.findAll('button').find((item) => item.text() === '举报')

    await button?.trigger('click')

    expect(wrapper.emitted('report')).toEqual([[comment()]])
  })

  it('自己的评论只能删除，不能举报自己', () => {
    const wrapper = mountItem(comment({ mine: true }))

    expect(wrapper.text()).toContain('删除')
    expect(wrapper.text()).not.toContain('举报')
  })

  it('二级回复同样带举报入口并向上抛出', async () => {
    const reply = comment({ id: 4, content: '同问' })
    const wrapper = mountItem(comment({ replies: [reply] }))
    const buttons = wrapper.findAll('button').filter((item) => item.text() === '举报')

    await buttons[1].trigger('click')

    expect(wrapper.emitted('report')).toEqual([[reply]])
  })
})
