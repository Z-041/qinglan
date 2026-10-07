import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchTopics } from '@/api/topic'
import { uploadImage } from '@/api/upload'
import ImagePicker from '@/components/ImagePicker.vue'
import PublishView from '@/views/PublishView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/topic', () => ({ fetchTopics: vi.fn() }))
vi.mock('@/api/post', () => ({ createPost: vi.fn() }))
vi.mock('@/api/upload', () => ({ uploadImage: vi.fn() }))

const mockedFetchTopics = vi.mocked(fetchTopics)
const mockedUpload = vi.mocked(uploadImage)

function file(name: string): File {
  return new File(['x'], name, { type: 'image/png' })
}

/** 上传成功后图片以 <img> 呈现，取 src 序列即可读到当前图片列表 */
function imageSources(wrapper: VueWrapper): (string | undefined)[] {
  return wrapper.findAll('img').map((img) => img.attributes('src'))
}

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
})
