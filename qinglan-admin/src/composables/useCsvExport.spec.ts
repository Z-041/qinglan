import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { saveBlob, timestampedFileName, useCsvExport } from '@/composables/useCsvExport'

const createObjectURL = vi.fn(() => 'blob:mock')
const revokeObjectURL = vi.fn()

describe('timestampedFileName', () => {
  it('按本地时间生成带时间戳的兜底 CSV 名', () => {
    const now = new Date(2026, 9, 9, 8, 7, 6)
    expect(timestampedFileName('users', now)).toBe('users-20261009-080706.csv')
  })
})

describe('saveBlob', () => {
  beforeEach(() => {
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL
  })

  it('用后端文件名触发下载并在之后释放 object URL', () => {
    let clickedName = ''
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        clickedName = this.download
      })

    saveBlob(new Blob(['a,b']), 'audit-logs-1.csv')

    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(clickedName).toBe('audit-logs-1.csv')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock')

    clickSpy.mockRestore()
  })
})

describe('useCsvExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('用后端 Content-Disposition 文件名落盘', async () => {
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)

    const { download, exporting } = useCsvExport()
    const ok = await download(async () => ({ blob: new Blob(['x']), fileName: 'users-20261009.csv' }), 'users')

    expect(ok).toBe(true)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(exporting.value).toBe(false)
  })

  it('后端不给文件名时用带时间戳的兜底名', async () => {
    let clickedName = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedName = this.download
    })

    const { download } = useCsvExport()
    await download(async () => ({ blob: new Blob(['x']), fileName: null }), 'audit-logs')

    expect(clickedName).toMatch(/^audit-logs-\d{8}-\d{6}\.csv$/)
  })

  it('请求失败（如导出超限）返回 false 且结束 exporting，错误由拦截器提示', async () => {
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)

    const { download, exporting } = useCsvExport()
    const ok = await download(async () => {
      throw new Error('QL-400-014')
    }, 'users')

    expect(ok).toBe(false)
    expect(exporting.value).toBe(false)
    // 失败不落盘
    expect(clickSpy).not.toHaveBeenCalled()
  })
})
