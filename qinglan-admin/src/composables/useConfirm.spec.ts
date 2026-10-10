import { ElMessageBox } from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useConfirm } from './useConfirm'

describe('useConfirm', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('确认返回 true，并把文案 / 标题 / 确认按钮透传（统一 warning + 取消文案）', async () => {
    const spy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const { confirm } = useConfirm()

    await expect(confirm('要发布吗？', '发布公告', '确认发布')).resolves.toBe(true)
    expect(spy).toHaveBeenCalledWith(
      '要发布吗？',
      '发布公告',
      expect.objectContaining({
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        type: 'warning',
      }),
    )
  })

  it('取消（reject）返回 false，不抛出', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')

    await expect(useConfirm().confirm('m', 't')).resolves.toBe(false)
  })

  it('未传确认文案时默认「确认」', async () => {
    const spy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)

    await useConfirm().confirm('m', 't')

    expect(spy).toHaveBeenCalledWith('m', 't', expect.objectContaining({ confirmButtonText: '确认' }))
  })
})
