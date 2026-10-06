import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchTopics } from '@/api/topic'
import PublishView from '@/views/PublishView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/topic', () => ({ fetchTopics: vi.fn() }))
vi.mock('@/api/post', () => ({ createPost: vi.fn() }))
vi.mock('@/api/upload', () => ({ uploadImage: vi.fn() }))

const mockedFetchTopics = vi.mocked(fetchTopics)

describe('PublishView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedFetchTopics.mockResolvedValue([])
  })

  it('渲染标题与右栏，且发布须知在 DOM 中（窄屏表单内那份也须存在）', async () => {
    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.text()).toContain('主帖先审后发，通过后才会出现在首页')
    // 右栏在窄屏被 max-lg:hidden 隐藏，须知由表单内那份承担；两处共用同一文案，
    // DOM 中必须能找到，否则窄屏会丢信息
    expect(wrapper.text()).toContain('禁止发布：姓名、学号、宿舍号、联系方式')
    expect(wrapper.findAll('aside')).toHaveLength(1)
  })

  it('未选话题时右栏「话题规则」卡展示占位文案', async () => {
    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.text()).toContain('选择一个话题后显示该话题的规则')
  })
})
