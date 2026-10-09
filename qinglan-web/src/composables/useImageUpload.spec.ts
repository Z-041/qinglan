import { beforeEach, describe, expect, it, vi } from 'vitest'

import { uploadImage, refreshImageUrls } from '@/api/upload'
import { MAX_IMAGES_PER_POST, useImageUpload } from '@/composables/useImageUpload'
import { useToast } from '@shared/composables/useToast'

vi.mock('@/api/upload', () => ({
  uploadImage: vi.fn(),
  refreshImageUrls: vi.fn(),
}))

const mockedUpload = vi.mocked(uploadImage)
const mockedRefresh = vi.mocked(refreshImageUrls)
const { toasts } = useToast()

function file(name: string): File {
  return new File(['x'], name, { type: 'image/png' })
}

describe('useImageUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    toasts.splice(0)
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

  it('超出的图片不静默丢弃：按真实新增数上传并提示忽略了几张', async () => {
    mockedUpload.mockResolvedValue({ url: '/uploads/only.jpg' })
    const { addFiles } = useImageUpload()
    // 已有 8 张，本次选 2 张：只能再收 1 张，另外 1 张必须被告知已忽略
    const almostFull = Array.from({ length: MAX_IMAGES_PER_POST - 1 }, (_, index) => `/uploads/${index}.jpg`)

    const added = await addFiles([file('a.png'), file('b.png')], almostFull)

    expect(added).toEqual(['/uploads/only.jpg'])
    expect(mockedUpload).toHaveBeenCalledTimes(1)
    expect(toasts.map((toast) => toast.message)).toEqual([
      `最多 ${MAX_IMAGES_PER_POST} 张图片，已忽略超出的 1 张`,
    ])
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

  it('refresh 只保留可用地址，并把换取不到的张数报出（草稿恢复）', async () => {
    mockedRefresh.mockResolvedValue([
      { url: '/uploads/a.jpg', available: true, usableUrl: '/uploads/a.jpg?exp=1&sig=x' },
      { url: '/uploads/b.jpg', available: false, usableUrl: null },
    ])
    const { refresh } = useImageUpload()

    const result = await refresh(['/uploads/a.jpg', '/uploads/b.jpg'])

    expect(mockedRefresh).toHaveBeenCalledWith(['/uploads/a.jpg', '/uploads/b.jpg'])
    expect(result).toEqual({ images: ['/uploads/a.jpg?exp=1&sig=x'], missing: 1 })
  })

  it('refresh 对空列表不发请求', async () => {
    const { refresh } = useImageUpload()

    expect(await refresh([])).toEqual({ images: [], missing: 0 })
    expect(mockedRefresh).not.toHaveBeenCalled()
  })
})
