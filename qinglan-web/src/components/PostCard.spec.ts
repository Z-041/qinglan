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

/** 行外层是 RouterLink，单测只关心内容，用桩替换以免引入路由实例 */
const RouterLinkStub = { template: '<a><slot /></a>' }

function mountCard(postData: PostSummary) {
  return mount(PostCard, {
    props: { post: postData },
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

  it('统一列表行使用 ql-row 分割线，不再用卡片浮起外壳', () => {
    const wrapper = mountCard(post())
    const article = wrapper.find('article')

    expect(article.classes()).toContain('ql-row')
    expect(article.classes()).not.toContain('ql-card-interactive')
    expect(wrapper.text()).toContain('问答')
    expect(wrapper.text()).toContain('银杏#abc')
    expect(wrapper.text()).toContain('有人在东门捡到校园卡吗')
  })

  it('时间用相对时间展示，并把绝对时间挂在 title 上', () => {
    const wrapper = mountCard(post())

    const time = wrapper.get('time')
    expect(time.attributes('datetime')).toBe('2026-10-06T00:00:00Z')
    expect(time.attributes('title')).toBeTruthy()
  })

  it('点赞按钮在链接之外，点「赞」不会触发链接', async () => {
    const wrapper = mountCard(post())
    const button = wrapper.get('button[aria-pressed]')

    // 结构不变量：<a> 的内容模型不允许出现 <button> 这类交互元素，按钮必须不在链接内部。
    expect(button.element.closest('a')).toBeNull()

    const outerClicks: Event[] = []
    wrapper.get('a').element.addEventListener('click', (event) => outerClicks.push(event))

    await button.trigger('click')

    expect(wrapper.emitted('toggle-like')).toHaveLength(1)
    // 按钮不在 <a> 内，事件自然不会冒泡到链接，点「赞」不会再连带跳进详情页
    expect(outerClicks).toHaveLength(0)
  })
})
