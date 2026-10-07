import { beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadImage } from '@/api/upload'
import { MAX_IMAGES_PER_POST, useImageUpload } from '@/composables/useImageUpload'

vi.mock('@/api/upload', () => ({
  uploadImage: vi.fn(),
}))

const mockedUpload = vi.mocked(uploadImage)

function file(name: string): File {
  return new File(['x'], name, { type: 'image/png' })
}

describe('useImageUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('只返回本次新增的地址，不含传入前的已有项', async () => {
    mockedUpload.mockResolvedValue({ url: '/uploads/new.jpg' })
    const { addFiles } = useImageUpload()

    const added = await addFiles([file('a.png')], ['/uploads/old.jpg'])

    expect(added).toEqual(['/uploads/new.jpg'])
  })

  it('上传失败时返回已成功的那部分，不抛异常', async () => {
    mockedUpload
      .mockResolvedValueOnce({ url: '/uploads/ok.jpg' })
      .mockRejectedValueOnce(new Error('500'))
    const { addFiles } = useImageUpload()

    const added = await addFiles([file('a.png'), file('b.png')], [])

    expect(added).toEqual(['/uploads/ok.jpg'])
  })

  it('已有图片达到上限时直接返回空数组，不发请求', async () => {
    const { addFiles } = useImageUpload()
    const full = Array.from({ length: MAX_IMAGES_PER_POST }, (_, index) => `/uploads/${index}.jpg`)

    const added = await addFiles([file('a.png')], full)

    expect(added).toEqual([])
    expect(mockedUpload).not.toHaveBeenCalled()
  })

  it('上传中 uploading 为 true，结束后复位', async () => {
    let release: (value: { url: string }) => void = () => {}
    mockedUpload.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    const { uploading, addFiles } = useImageUpload()

    const pending = addFiles([file('a.png')], [])
    expect(uploading.value).toBe(true)

    release({ url: '/uploads/a.jpg' })
    await pending

    expect(uploading.value).toBe(false)
  })

  it('调用方按「当时的列表」合并：上传期间删掉的图不会被复活', async () => {
    let release: (value: { url: string }) => void = () => {}
    mockedUpload.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve
        }),
    )
    const { addFiles } = useImageUpload()

    // 模拟调用方的合并逻辑（PublishView.handleSelectImages 的同款写法）
    let images = ['/uploads/keep.jpg', '/uploads/removed.jpg']
    const pending = addFiles([file('new.png')], images).then((added) => {
      images = [...images, ...added.filter((url) => !images.includes(url))]
    })

    // 上传在途时用户删掉一张
    images = images.filter((url) => url !== '/uploads/removed.jpg')
    release({ url: '/uploads/new.jpg' })
    await pending

    expect(images).toEqual(['/uploads/keep.jpg', '/uploads/new.jpg'])
  })
})
