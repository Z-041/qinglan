import { afterEach, describe, expect, it } from 'vitest'

import { messageBoxState, resolveMessageBox } from './useMessageBox'
import { useReasonPrompt } from './useReasonPrompt'

afterEach(() => {
  // 兜底复位：即使某条用例提前断言失败，也不让单例状态泄漏到下一条
  resolveMessageBox(false)
})

describe('useReasonPrompt', () => {
  it('确认时返回去空格后的理由，期间 promptOpen 置真、结束后复位', async () => {
    const { promptOpen, promptReason } = useReasonPrompt()
    expect(promptOpen.value).toBe(false)

    const pending = promptReason('驳回帖子', '驳回理由会发给作者', '确认')
    // 弹窗打开期间必须为真：两个队列据此让轮询闭嘴
    expect(promptOpen.value).toBe(true)
    expect(messageBoxState.request?.kind).toBe('prompt')
    expect(messageBoxState.request?.confirmText).toBe('确认')
    expect(messageBoxState.request?.placeholder).toBe('理由')

    messageBoxState.input = '  违规内容  '
    resolveMessageBox(true)

    await expect(pending).resolves.toBe('违规内容')
    expect(promptOpen.value).toBe(false)
  })

  it('用户取消返回 null 并把 promptOpen 复位，不抛出', async () => {
    const { promptOpen, promptReason } = useReasonPrompt()

    const pending = promptReason('解除封禁', '请填写解封理由', '确认解封')
    resolveMessageBox(false)

    await expect(pending).resolves.toBeNull()
    expect(promptOpen.value).toBe(false)
  })

  it('非空校验统一：纯空格被拒，正常值放行', () => {
    void useReasonPrompt().promptReason('权限变更', '理由会写入审计日志')

    const validate = messageBoxState.request!.validate
    expect(validate('   ')).toBe('理由不能为空')
    expect(validate('ok')).toBeNull()
  })
})
