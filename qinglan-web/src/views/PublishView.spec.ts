import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchTopics } from '@/api/topic'
import { refreshImageUrls, uploadImage } from '@/api/upload'
import ImagePicker from '@/components/ImagePicker.vue'
import { POST_DRAFT_STORAGE_KEY } from '@/composables/usePostDraft'
import PublishView from '@/views/PublishView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/topic', () => ({ fetchTopics: vi.fn() }))
vi.mock('@/api/post', () => ({ createPost: vi.fn() }))
vi.mock('@/api/upload', () => ({ uploadImage: vi.fn(), refreshImageUrls: vi.fn() }))

const mockedFetchTopics = vi.mocked(fetchTopics)
const mockedUpload = vi.mocked(uploadImage)
const mockedRefresh = vi.mocked(refreshImageUrls)

function file(name: string): File {
  return new File(['x'], name, { type: 'image/png' })
}

/** 上传成功后图片以 <img> 呈现，取 src 序列即可读到当前图片列表 */
function imageSources(wrapper: VueWrapper): (string | undefined)[] {
  return wrapper.findAll('img').map((img) => img.attributes('src'))
}

function textareaValue(wrapper: VueWrapper): string {
  return (wrapper.get('textarea').element as HTMLTextAreaElement).value
}

describe('PublishView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 草稿落在 localStorage，测试间必须清掉，否则上一用例的草稿会漏进下一用例
    localStorage.clear()
    mockedFetchTopics.mockResolvedValue([])
  })

  it('渲染标题与右栏，且发布须知在 DOM 中（窄屏表单内那份也须存在）', async () => {
    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.text()).toContain('主帖先审后发，通过后才会出现在首页')
    // 右栏在窄屏被 max-lg:hidden 隐藏，须知由表单内那份承担；两处共用同一文案，
    // 因此必须**恰好两处**都存在——只断言「包含」的话，删掉任意一处仍会通过
    const notices = wrapper
      .findAll('p')
      .filter((element) => element.text().includes('禁止发布：姓名、学号、宿舍号、联系方式'))
    expect(notices).toHaveLength(2)
    expect(wrapper.findAll('aside')).toHaveLength(1)
  })

  it('未选话题时右栏「话题规则」卡展示占位文案', async () => {
    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.text()).toContain('选择一个话题后显示该话题的规则')
  })

  it('上传期间删掉的图不会被复活（真实组件的合并行为）', async () => {
    // 先让两张图上传成功
    mockedUpload
      .mockResolvedValueOnce({ url: '/uploads/a.jpg' })
      .mockResolvedValueOnce({ url: '/uploads/b.jpg' })
    const wrapper = mount(PublishView)
    await flushPromises()

    const picker = wrapper.findComponent(ImagePicker)
    picker.vm.$emit('select', [file('a.png'), file('b.png')])
    await flushPromises()
    expect(imageSources(wrapper)).toEqual(['/uploads/a.jpg', '/uploads/b.jpg'])

    // 第三次上传挂起在途
    let release: (value: { url: string }) => void = () => {}
    mockedUpload.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    picker.vm.$emit('select', [file('c.png')])
    await flushPromises()

    // 上传在途时用户删掉第二张（若合并基于 await 前的快照，这张会「复活」）
    const removeButtons = wrapper.findAll('button[aria-label="移除图片"]')
    await removeButtons[1].trigger('click')

    release({ url: '/uploads/c.jpg' })
    await flushPromises()

    expect(imageSources(wrapper)).toEqual(['/uploads/a.jpg', '/uploads/c.jpg'])
  })

  it('检出本地草稿时给出「恢复 / 丢弃」，不静默覆盖当前输入', async () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 1, content: '旧草稿正文', contact: '', images: [] }),
    )

    const wrapper = mount(PublishView)
    await flushPromises()

    expect(wrapper.text()).toContain('发现上次未提交的草稿')
    // 未点「恢复」前不得把草稿填进表单（否则会覆盖用户新输入）
    expect(textareaValue(wrapper)).toBe('')

    await wrapper.findAll('button').find((button) => button.text() === '恢复草稿')?.trigger('click')
    await flushPromises()

    expect(textareaValue(wrapper)).toBe('旧草稿正文')
    expect(wrapper.text()).not.toContain('发现上次未提交的草稿')
  })

  it('恢复含图片的草稿：用批量端点换取可用地址，取不到的剔除（不静默破图）', async () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({
        topicId: 1,
        content: '带图草稿',
        contact: '',
        images: ['/uploads/a.jpg', '/uploads/b.jpg'],
      }),
    )
    mockedRefresh.mockResolvedValue([
      { url: '/uploads/a.jpg', available: true, usableUrl: '/uploads/a.jpg?exp=1&sig=x' },
      { url: '/uploads/b.jpg', available: false, usableUrl: null },
    ])

    const wrapper = mount(PublishView)
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '恢复草稿')?.trigger('click')
    await flushPromises()

    // 用草稿里的原 URL 换取当前可用地址；可用的换成签名 URL，取不到的从列表剔除
    expect(mockedRefresh).toHaveBeenCalledWith(['/uploads/a.jpg', '/uploads/b.jpg'])
    expect(imageSources(wrapper)).toEqual(['/uploads/a.jpg?exp=1&sig=x'])
  })

  it('点「丢弃」后本地草稿被清除，不再提示恢复', async () => {
    localStorage.setItem(
      POST_DRAFT_STORAGE_KEY,
      JSON.stringify({ topicId: 1, content: '旧草稿正文', contact: '', images: [] }),
    )

    const wrapper = mount(PublishView)
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '丢弃')?.trigger('click')
    await flushPromises()

    expect(localStorage.getItem(POST_DRAFT_STORAGE_KEY)).toBeNull()
    expect(wrapper.text()).not.toContain('发现上次未提交的草稿')
  })

  it('表单字段变更后自动写入本地草稿', async () => {
    const wrapper = mount(PublishView)
    await flushPromises()

    await wrapper.get('textarea').setValue('写了一半的内容')
    await flushPromises()

    const raw = localStorage.getItem(POST_DRAFT_STORAGE_KEY)
    expect(raw).not.toBeNull()
    expect(JSON.parse(raw as string).content).toBe('写了一半的内容')
  })
})
