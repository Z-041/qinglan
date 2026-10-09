import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPosts } from '@/api/post'
import { fetchTopics } from '@/api/topic'
import PostCard from '@/components/PostCard.vue'
import HomeView from '@/views/HomeView.vue'
import type { PostSummary, Topic } from '@/types/post'

vi.mock('@/api/post', () => ({
  fetchPosts: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}))

vi.mock('@/api/topic', () => ({
  fetchTopics: vi.fn(),
}))

const mockedFetchPosts = vi.mocked(fetchPosts)
const mockedFetchTopics = vi.mocked(fetchTopics)

/** 首页外层是 RouterLink（游客卡、空态按钮），单测用桩替换以免引入路由实例 */
const RouterLinkStub = { template: '<a><slot /></a>' }

function post(id: number): PostSummary {
  return {
    id,
    topicId: 1,
    topicName: '问答',
    content: `第 ${id} 条`,
    images: [],
    anonymousCode: `银杏#${id}`,
    likeCount: 0,
    liked: false,
    commentCount: 0,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
  }
}

function topic(id: number): Topic {
  return { id, name: `话题${id}`, rules: `话题${id}的规则`, requiresContact: false }
}

function mountHome() {
  return mount(HomeView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('HomeView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockedFetchTopics.mockResolvedValue([])
  })

  it('所有帖子统一渲染为分割线列表行（不再分头条大卡与紧凑行）', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(1), post(2), post(3)], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.findAllComponents(PostCard)).toHaveLength(3)
    // 行级样式来自 ql-row；不再有卡片浮起的 ql-card-interactive
    expect(wrapper.findAll('article.ql-row')).toHaveLength(3)
    expect(wrapper.find('.ql-card-interactive').exists()).toBe(false)
    // 原 hero 横幅与口号已删除
    expect(wrapper.text()).not.toContain('校园里的事，匿名说出来')
    expect(wrapper.text()).toContain('第 2 条')
    expect(wrapper.text()).toContain('第 3 条')
  })

  it('渲染「最新 / 热门」两个排序 Tab，默认选中最新', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(1)], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()

    const tabs = wrapper.findAll('button').filter((b) => ['最新', '热门'].includes(b.text()))
    expect(tabs.map((b) => b.text())).toEqual(['最新', '热门'])
    expect(tabs[0].attributes('aria-pressed')).toBe('true')
    expect(tabs[1].attributes('aria-pressed')).toBe('false')
  })

  it('点击「热门」按 sort=hot 重置到第一页；再点「最新」切回 latest', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(1)], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()
    mockedFetchPosts.mockClear()

    mockedFetchPosts.mockResolvedValue({ items: [post(9)], nextCursor: null })
    await wrapper.findAll('button').find((b) => b.text() === '热门')?.trigger('click')
    await flushPromises()

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: null, sort: 'hot', cursor: null })
    expect(wrapper.text()).toContain('第 9 条')

    await wrapper.findAll('button').find((b) => b.text() === '最新')?.trigger('click')
    await flushPromises()

    expect(mockedFetchPosts).toHaveBeenLastCalledWith({ topic: null, sort: 'latest', cursor: null })
  })

  it('空列表不渲染任何卡片，只渲染空态', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.findAllComponents(PostCard)).toHaveLength(0)
    expect(wrapper.text()).toContain('这里还没有内容')
  })

  it('未认证时展示认证引导卡', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('认证后即可匿名发帖与互动')
  })

  it('话题加载失败时给出提示与重试，而不是静默消失', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [], nextCursor: null })
    mockedFetchTopics.mockRejectedValue(new Error('网络异常'))

    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('话题没能加载出来')
    expect(wrapper.findAll('button').some((button) => button.text() === '重试')).toBe(true)
  })

  it('切换话题后按该话题重新拉取第一页', async () => {
    mockedFetchTopics.mockResolvedValue([topic(3)])
    mockedFetchPosts.mockResolvedValue({ items: [post(1)], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()
    mockedFetchPosts.mockClear()
    mockedFetchPosts.mockResolvedValue({ items: [post(9)], nextCursor: null })

    const chip = wrapper.findAll('button').find((button) => button.text() === '话题3')
    await chip?.trigger('click')
    await flushPromises()

    expect(mockedFetchPosts).toHaveBeenCalledWith({ topic: 3, sort: 'latest', cursor: null })
    expect(wrapper.text()).toContain('第 9 条')
    expect(wrapper.text()).not.toContain('第 1 条')
  })
})
