import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReviewActionBar from '@/components/admin/ReviewActionBar.vue'
import type { ReviewTargetType } from '@/types/review'

function mountBar(targetType: ReviewTargetType, targetStatus: string, submitting = false) {
  return mount(ReviewActionBar, { props: { targetType, targetStatus, submitting } })
}

function labels(targetType: ReviewTargetType, targetStatus: string): string[] {
  return mountBar(targetType, targetStatus)
    .findAll('button')
    .map((button) => button.text())
}

describe('ReviewActionBar', () => {
  it('评论只给「移除评论 / 举报不成立」，不出现帖子类动作', () => {
    expect(labels('COMMENT', 'PENDING')).toEqual(['移除评论', '举报不成立'])
  })

  it('审核中的帖子可「通过 / 驳回」，且此时不给「恢复可见」', () => {
    const result = labels('POST', 'PENDING')

    expect(result).toContain('通过')
    expect(result).toContain('驳回')
    expect(result).toContain('下架')
    expect(result).not.toContain('恢复可见')
  })

  it('已下架的帖子给「恢复可见 / 维持下架」，且不再给「下架」', () => {
    const result = labels('POST', 'TAKEN_DOWN')

    expect(result).toContain('恢复可见')
    expect(result).toContain('维持下架')
    expect(result).not.toContain('下架')
    expect(result).not.toContain('通过')
  })

  it('联系方式的隐藏与恢复常驻，保证两条路径都走得到', () => {
    for (const status of ['PENDING', 'APPROVED', 'TAKEN_DOWN']) {
      const result = labels('POST', status)

      expect(result).toContain('隐藏联系方式')
      expect(result).toContain('恢复联系方式')
      expect(result).toContain('举报不成立')
    }
  })

  it('点击按钮抛出对应的动作 key，而不是中文标签', async () => {
    const wrapper = mountBar('POST', 'PENDING')

    const takeDown = wrapper.findAll('button').find((button) => button.text() === '下架')
    await takeDown?.trigger('click')

    expect(wrapper.emitted('action')).toEqual([['take-down']])
  })

  it('提交中时所有动作按钮都禁用，避免重复处置', () => {
    const wrapper = mountBar('POST', 'PENDING', true)

    for (const button of wrapper.findAll('button')) {
      expect((button.element as HTMLButtonElement).disabled).toBe(true)
    }
  })
})
