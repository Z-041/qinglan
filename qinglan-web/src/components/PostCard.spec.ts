import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PostCard from './PostCard.vue'
import type { PostSummary } from '@/types/post'

function post(overrides: Partial<PostSummary> = {}): PostSummary {
  return {
    id: 9,
    topicId: 1,
    topicName: '问答',
    content: '有人在东门捡到校园卡吗',
    images: [],
    anonymousCode: '银杏#abc',
    likeCount: 3,
    liked: false,
    commentCount: 2,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

/** 卡片外层是 RouterLink，单测只关心内容，用桩替换以免引入路由实例 */
const RouterLinkStub = { template: '<a><slot /></a>' }

function mountCard(postData: PostSummary, variant?: 'feature' | 'compact') {
  return mount(PostCard, {
    props: variant ? { post: postData, variant } : { post: postData },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('PostCard', () => {
  it('展示话题、匿名代号、正文与互动计数', () => {
    const wrapper = mountCard(post())

    expect(wrapper.text()).toContain('问答')
    expect(wrapper.text()).toContain('银杏#abc')
    expect(wrapper.text()).toContain('有人在东门捡到校园卡吗')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('2')
  })

  it('点击点赞按钮抛出 toggle-like', async () => {
    const wrapper = mountCard(post())

    await wrapper.get('button[aria-pressed]').trigger('click')

    expect(wrapper.emitted('toggle-like')).toHaveLength(1)
  })

  it('本人所发时展示「我」标记', () => {
    const wrapper = mountCard(post({ mine: true }))

    expect(wrapper.text()).toContain('我')
  })

  it('有图片时按数量渲染配图', () => {
    const wrapper = mountCard(post({ images: ['/uploads/a.jpg', '/uploads/b.jpg'] }))

    expect(wrapper.findAll('img')).toHaveLength(2)
  })

  it('点赞后高亮态随 props 变化', async () => {
    const wrapper = mountCard(post({ liked: false }))
    expect(wrapper.get('button[aria-pressed]').attributes('aria-pressed')).toBe('false')

    await wrapper.setProps({ post: post({ liked: true }) })
    expect(wrapper.get('button[aria-pressed]').attributes('aria-pressed')).toBe('true')
  })

  it('compact 变体去掉卡片外壳，仍展示话题、代号、正文与计数', () => {
    const wrapper = mountCard(post(), 'compact')

    expect(wrapper.find('article').classes()).not.toContain('ql-card-interactive')
    expect(wrapper.text()).toContain('问答')
    expect(wrapper.text()).toContain('银杏#abc')
    expect(wrapper.text()).toContain('有人在东门捡到校园卡吗')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('2')
  })

  it('compact 变体点击点赞仍抛出 toggle-like', async () => {
    const wrapper = mountCard(post(), 'compact')

    await wrapper.get('button[aria-pressed]').trigger('click')

    expect(wrapper.emitted('toggle-like')).toHaveLength(1)
  })
})
