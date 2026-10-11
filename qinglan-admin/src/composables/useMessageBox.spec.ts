import { afterEach, describe, expect, it } from 'vitest'

import { messageBoxState, resolveMessageBox, useMessageBox } from './useMessageBox'

afterEach(() => {
  // 兜底复位：即使某条用例提前断言失败，也不让状态泄漏到下一条
  resolveMessageBox(false)
})

describe('useMessageBox', () => {
  it('confirm：确认解析 true，取消解析 false，文案透传', async () => {
    const confirmed = useMessageBox().confirm('要发布吗？', '发布公告', '确认发布')
    expect(messageBoxState.request?.kind).toBe('confirm')
    expect(messageBoxState.request?.title).toBe('发布公告')
    expect(messageBoxState.request?.confirmText).toBe('确认发布')
    expect(messageBoxState.request?.cancelText).toBe('取消')

    resolveMessageBox(true)
    await expect(confirmed).resolves.toBe(true)

    const cancelled = useMessageBox().confirm('m', 't')
    resolveMessageBox(false)
    await expect(cancelled).resolves.toBe(false)
  })

  it('promptReason：确认返回去空格后的理由，取消返回 null', async () => {
    const accepted = useMessageBox().promptReason('驳回帖子', '驳回理由会发给作者')
    expect(messageBoxState.request?.kind).toBe('prompt')
    expect(messageBoxState.request?.placeholder).toBe('理由')

    messageBoxState.input = '  违规内容  '
    resolveMessageBox(true)
    await expect(accepted).resolves.toBe('违规内容')

    const cancelled = useMessageBox().promptReason('解除封禁', '请填写解封理由', '确认解封')
    resolveMessageBox(false)
    await expect(cancelled).resolves.toBeNull()
  })

  it('promptReason 的非空校验：纯空格被拒、正常值放行', () => {
    void useMessageBox().promptReason('权限变更', '理由会写入审计日志')

    const validate = messageBoxState.request!.validate
    expect(validate('   ')).toBe('理由不能为空')
    expect(validate('ok')).toBeNull()
  })
})
