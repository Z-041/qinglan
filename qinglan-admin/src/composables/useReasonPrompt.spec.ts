import { ElMessageBox } from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useReasonPrompt } from './useReasonPrompt'

/**
 * element-plus 的 MessageBoxData 是个夹了字符串联合的交叉类型，字面量 { value, action } 直接
 * 赋给 mockResolvedValue 会 typecheck 报错；这里统一用 `as never` 绕开该类型怪癖，
 * 不改变被测行为（被测代码只读取 result.value）。
 */
function resolved(value: string): never {
  return { value, action: 'confirm' } as never
}

describe('useReasonPrompt', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('确认时返回去空格后的理由，期间 promptOpen 置真、结束后复位', async () => {
    const promptSpy = vi.spyOn(ElMessageBox, 'prompt').mockResolvedValue(resolved('  违规内容  '))
    const { promptOpen, promptReason } = useReasonPrompt()

    const pending = promptReason('驳回帖子', '驳回理由会发给作者', '确认')
    // 弹窗打开期间必须为真：两个队列据此让轮询闭嘴
    expect(promptOpen.value).toBe(true)

    await expect(pending).resolves.toBe('违规内容')
    expect(promptOpen.value).toBe(false)
    expect(promptSpy).toHaveBeenCalledWith(
      '驳回理由会发给作者',
      '驳回帖子',
      expect.objectContaining({
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '理由',
      }),
    )
  })

  it('用户取消（reject）返回 null 并把 promptOpen 复位，不抛出', async () => {
    vi.spyOn(ElMessageBox, 'prompt').mockRejectedValue('cancel')
    const { promptOpen, promptReason } = useReasonPrompt()

    await expect(promptReason('解除封禁', '请填写解封理由', '确认解封')).resolves.toBeNull()
    expect(promptOpen.value).toBe(false)
  })

  it('非空校验统一：纯空格被拒，正常值放行', () => {
    const promptSpy = vi.spyOn(ElMessageBox, 'prompt').mockResolvedValue(resolved('ok'))

    void useReasonPrompt().promptReason('权限变更', '理由会写入审计日志')

    const options = promptSpy.mock.calls[0][2] as { inputValidator: (value: string) => unknown }
    expect(options.inputValidator('   ')).toBe('理由不能为空')
    expect(options.inputValidator('ok')).toBe(true)
  })
})
