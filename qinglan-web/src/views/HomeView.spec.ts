import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchPosts } from '@/api/post'
import { fetchTopics } from '@/api/topic'
import PostCard from '@/components/PostCard.vue'
import HomeView from '@/views/HomeView.vue'
import type { PostSummary } from '@/types/post'

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

/** 首页外层是 RouterLink（Hero 按钮、游客卡），单测用桩替换以免引入路由实例 */
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

function mountHome() {
  return mount(HomeView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('HomeView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockedFetchTopics.mockResolvedValue([])
  })

  it('首条渲染为 feature 大卡，其余渲染为 compact 紧凑行', async () => {
    mockedFetchPosts.mockResolvedValue({ items: [post(1), post(2), post(3)], nextCursor: null })

    const wrapper = mountHome()
    await flushPromises()

    const variants = wrapper.findAllComponents(PostCard).map((card) => card.props('variant'))
    expect(variants).toEqual(['feature', 'compact', 'compact'])
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
})
