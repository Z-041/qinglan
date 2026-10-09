import { describe, expect, it } from 'vitest'

import { REVIEW_ACTION_FLOW, type ActionInput } from '@/composables/reviewActionFlow'
import type { ReviewAction } from '@/types/review'

describe('REVIEW_ACTION_FLOW', () => {
  it('覆盖全部处置动作，且输入类别归类正确', () => {
    const expected: Record<ReviewAction, ActionInput> = {
      approve: 'direct',
      reject: 'reason',
      restore: 'reason',
      'show-contact': 'reason',
      dismiss: 'dialog',
      'take-down': 'dialog',
      'hide-contact': 'dialog',
      uphold: 'dialog',
      'remove-comment': 'dialog',
    }
    expect(REVIEW_ACTION_FLOW).toEqual(expected)
  })

  it('无需输入的动作只有「通过」', () => {
    const direct = Object.entries(REVIEW_ACTION_FLOW)
      .filter(([, input]) => input === 'direct')
      .map(([action]) => action)
    expect(direct).toEqual(['approve'])
  })

  it('需填自由文本理由的动作是驳回 / 恢复可见 / 恢复联系方式', () => {
    const reason = Object.entries(REVIEW_ACTION_FLOW)
      .filter(([, input]) => input === 'reason')
      .map(([action]) => action)
      .sort()
    expect(reason).toEqual(['reject', 'restore', 'show-contact'].sort())
  })
})
