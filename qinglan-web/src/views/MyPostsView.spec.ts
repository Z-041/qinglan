import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchMyPosts } from '@/api/post'
import MyPostsView from '@/views/MyPostsView.vue'
import type { MyPostItem } from '@/types/post'

vi.mock('@/api/post', () => ({ fetchMyPosts: vi.fn() }))

const mockedFetchMyPosts = vi.mocked(fetchMyPosts)

/** 视图里的入口 / 空态都是 RouterLink，用桩替换以免引入路由实例 */
const RouterLinkStub = { props: ['to'], template: '<a :href="to"><slot /></a>' }

function item(id: number, overrides: Partial<MyPostItem> = {}): MyPostItem {
  return {
    id,
    topicId: 1,
    topicName: '问答',
    content: `第 ${id} 条`,
    images: [],
    anonymousCode: `银杏#${id}`,
    status: 'PENDING',
    reviewReason: null,
    likeCount: 0,
    commentCount: 0,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

function mountView() {
  return mount(MyPostsView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('MyPostsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('渲染帖子行，覆盖全部状态并展示中文状态标签', async () => {
    mockedFetchMyPosts.mockResolvedValue({
      items: [
        item(1, { status: 'PENDING' }),
        item(2, { status: 'APPROVED' }),
        item(3, { status: 'REJECTED', reviewReason: '与话题无关' }),
        item(4, { status: 'TAKEN_DOWN', reviewReason: 'ABUSE: 辱骂' }),
      ],
      nextCursor: null,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.text()).toContain('已通过')
    expect(wrapper.text()).toContain('已驳回')
    expect(wrapper.text()).toContain('已下架')
    expect(wrapper.findAll('li.ql-row')).toHaveLength(4)
  })

  it('驳回展示自由文本原因，下架展示映射后的原因', async () => {
    mockedFetchMyPosts.mockResolvedValue({
      items: [
        item(3, { status: 'REJECTED', reviewReason: '与话题无关' }),
        item(4, { status: 'TAKEN_DOWN', reviewReason: 'ABUSE: 辱骂' }),
      ],
      nextCursor: null,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('驳回原因：与话题无关')
    // ABUSE 经 useUserLabels 映射为「辱骂、人身攻击」，下架原因走同一口径
    expect(wrapper.text()).toContain('下架原因：辱骂、人身攻击：辱骂')
  })

  it('空列表渲染空态与发布入口', async () => {
    mockedFetchMyPosts.mockResolvedValue({ items: [], nextCursor: null })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('你还没有发布过帖子')
    expect(wrapper.text()).toContain('去发布')
  })

  it('加载失败渲染失败态与重试，而不是空态', async () => {
    mockedFetchMyPosts.mockRejectedValue(new Error('网络异常'))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('帖子没能加载出来')
    expect(wrapper.text()).not.toContain('你还没有发布过帖子')
    expect(wrapper.findAll('button').some((b) => b.text() === '重试')).toBe(true)
  })
})
