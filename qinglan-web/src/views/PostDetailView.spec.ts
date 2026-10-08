import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deleteComment, fetchComments } from '@/api/comment'
import { setAuthToken } from '@shared/api/http'
import { fetchPostDetail } from '@/api/post'
import { useUserStore } from '@shared/stores/user'
import PostDetailView from '@/views/PostDetailView.vue'
import type { Comment } from '@/types/comment'
import type { PostDetail } from '@/types/post'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
}))

vi.mock('@/api/post', () => ({
  fetchPostDetail: vi.fn(),
  likePost: vi.fn(),
  unlikePost: vi.fn(),
}))

vi.mock('@/api/comment', () => ({
  fetchComments: vi.fn(),
  createComment: vi.fn(),
  deleteComment: vi.fn(),
  likeComment: vi.fn(),
  unlikeComment: vi.fn(),
}))

vi.mock('@shared/api/report', () => ({
  createReport: vi.fn(),
  submitAppeal: vi.fn(),
}))

const mockedFetchPostDetail = vi.mocked(fetchPostDetail)
const mockedFetchComments = vi.mocked(fetchComments)
const mockedDeleteComment = vi.mocked(deleteComment)

function detail(overrides: Partial<PostDetail> = {}): PostDetail {
  return {
    id: 7,
    topicId: 1,
    topicName: '问答',
    topicRules: '话题规则',
    content: '正文',
    contact: null,
    contactVisible: false,
    images: [],
    anonymousCode: '银杏#7',
    likeCount: 0,
    liked: false,
    commentCount: 0,
    mine: false,
    reviewReason: null,
    takenDown: false,
    createdAt: '2026-10-06T00:00:00Z',
    ...overrides,
  }
}

const RouterLinkStub = { props: ['to'], template: '<a :href="to"><slot /></a>' }

async function mountView(
  post: PostDetail,
  comments: Comment[] = [],
  nextCursor: string | null = null,
): Promise<VueWrapper> {
  mockedFetchPostDetail.mockResolvedValue(post)
  mockedFetchComments.mockResolvedValue({ items: comments, nextCursor })
  const wrapper = mount(PostDetailView, { global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

function comment(id: number, overrides: Partial<Comment> = {}): Comment {
  return {
    id,
    content: `评论内容 ${id}`,
    anonymousCode: `梧桐#${id}`,
    likeCount: 0,
    liked: false,
    mine: false,
    createdAt: '2026-10-06T00:00:00Z',
    replies: [],
    ...overrides,
  }
}

describe('PostDetailView 联系方式（不变量 #2）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    // 令牌以 api/http 的内存态为准，模块级状态不会自动复位，必须显式清掉
    setAuthToken(null)
    vi.clearAllMocks()
  })

  it('游客看到认证引导，且响应里本就没有联系方式', async () => {
    const wrapper = await mountView(detail())

    expect(wrapper.text()).toContain('认证后可查看联系方式')
  })

  it('已认证但服务端未回填 contact 时，不武断声称「作者未提供」（可能是被处置隐藏）', async () => {
    useUserStore().setAccessToken('token')

    const wrapper = await mountView(detail({ contact: null, contactVisible: false }))

    expect(wrapper.text()).toContain('作者未提供联系方式，或已被管理员隐藏')
    expect(wrapper.text()).not.toContain('可查看联系方式')
  })

  it('服务端判定可见时如实回显联系方式', async () => {
    useUserStore().setAccessToken('token')

    const wrapper = await mountView(detail({ contact: 'wechat: qinglan', contactVisible: true }))

    expect(wrapper.text()).toContain('wechat: qinglan')
  })
})

describe('PostDetailView 删除评论', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    setAuthToken(null)
    vi.clearAllMocks()
    useUserStore().setAccessToken('token')
  })

  function myComment(): Comment {
    return {
      id: 3,
      content: '我的评论',
      anonymousCode: '梧桐#3',
      likeCount: 0,
      liked: false,
      mine: true,
      createdAt: '2026-10-06T00:00:00Z',
      replies: [],
    }
  }

  it('先弹自绘二次确认，确认后才真正删除（不再用 window.confirm）', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm')
    mockedDeleteComment.mockResolvedValue(undefined)
    const wrapper = await mountView(detail({ mine: true }), [myComment()])

    await wrapper.findAll('button').find((button) => button.text() === '删除')?.trigger('click')
    await flushPromises()

    // 未确认前不得发请求，也不得调用原生 confirm
    expect(mockedDeleteComment).not.toHaveBeenCalled()
    expect(confirmSpy).not.toHaveBeenCalled()

    const dialog = wrapper.find('[role="dialog"][aria-label="删除评论"]')
    expect(dialog.exists()).toBe(true)

    await dialog.findAll('button').find((button) => button.text() === '删除')?.trigger('click')
    await flushPromises()

    expect(mockedDeleteComment).toHaveBeenCalledWith(3)
    confirmSpy.mockRestore()
  })

  it('取消后不发请求且关闭确认框', async () => {
    mockedDeleteComment.mockResolvedValue(undefined)
    const wrapper = await mountView(detail({ mine: true }), [myComment()])

    await wrapper.findAll('button').find((button) => button.text() === '删除')?.trigger('click')
    await flushPromises()

    const dialog = wrapper.find('[role="dialog"][aria-label="删除评论"]')
    await dialog.findAll('button').find((button) => button.text() === '取消')?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"][aria-label="删除评论"]').exists()).toBe(false)
    expect(mockedDeleteComment).not.toHaveBeenCalled()
  })
})

describe('PostDetailView 评论分页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    setAuthToken(null)
    vi.clearAllMocks()
    useUserStore().setAccessToken('token')
  })

  function moreButton(wrapper: VueWrapper) {
    return wrapper.findAll('button').find((button) => button.text() === '加载更多')
  }

  it('末页（nextCursor 为 null）不显示「加载更多」', async () => {
    const wrapper = await mountView(detail(), [comment(1)], null)

    expect(moreButton(wrapper)).toBeUndefined()
  })

  it('还有下一页时显示「加载更多」，点击后按游标追加，末页后收起', async () => {
    const wrapper = await mountView(detail(), [comment(1)], 'cursor-1')
    expect(moreButton(wrapper)).toBeTruthy()

    mockedFetchComments.mockResolvedValueOnce({ items: [comment(2)], nextCursor: null })
    await moreButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(mockedFetchComments).toHaveBeenLastCalledWith(7, 'cursor-1')
    expect(wrapper.text()).toContain('评论内容 2')
    // 已到末页，入口收起
    expect(moreButton(wrapper)).toBeUndefined()
  })
})
