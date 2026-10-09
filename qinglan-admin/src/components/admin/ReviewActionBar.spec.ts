import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import ReviewActionBar from '@/components/admin/ReviewActionBar.vue'
import { usePermissions } from '@/composables/usePermissions'
import type { AdminPermission } from '@/types/permission'
import type { ReviewTargetType } from '@shared/types/review-common'

function mountBar(
  targetType: ReviewTargetType,
  targetStatus: string,
  submitting = false,
  hasOpenProposal = false,
  // 默认给 true，让「按状态给哪些动作」的用例聚焦在动作集合上；
  // 撤销权限本身的行为由下面专门的用例覆盖（含「不传即隐藏」的安全默认）
  canRevoke = true,
) {
  return mount(ReviewActionBar, {
    props: { targetType, targetStatus, submitting, hasOpenProposal, canRevoke },
  })
}

function labels(targetType: ReviewTargetType, targetStatus: string): string[] {
  return mountBar(targetType, targetStatus)
    .findAll('button')
    .map((button) => button.text())
}

describe('ReviewActionBar', () => {
  /** 按钮已按权限点过滤：先授予全部相关权限，让「按状态给哪些动作」的用例聚焦动作集合 */
  const ALL_PERMISSIONS: AdminPermission[] = [
    'POST_REVIEW',
    'PROPOSAL_CREATE',
    'REPORT_DISMISS',
    'DISPOSITION_REVOKE',
  ]

  beforeEach(() => {
    usePermissions().permissions.value = [...ALL_PERMISSIONS]
  })

  afterEach(() => {
    // usePermissions 是模块级单例，测试之间不会自动复位
    usePermissions().reset()
  })

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

  it('有撤销权限时，联系方式的隐藏与恢复在各状态下都给出，保证两条路径都走得到', () => {
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

  it('提交中只有被点击的动作显示 loading，其余按钮只禁用不转圈', async () => {
    const wrapper = mountBar('POST', 'PENDING')

    const approve = wrapper.findAll('button').find((button) => button.text() === '通过')
    await approve?.trigger('click')
    await wrapper.setProps({ submitting: true })

    const spinning = wrapper.findAll('button').filter((button) => button.find('.animate-spin').exists())
    expect(spinning).toHaveLength(1)
    expect(spinning[0].text()).toBe('通过')
    // 其余按钮仍需禁用（防重复处置），只是不显示 loading
    for (const button of wrapper.findAll('button')) {
      expect((button.element as HTMLButtonElement).disabled).toBe(true)
    }
  })

  it('已有进行中提案时，走表决的动作被禁用并给出原因，单人动作仍可用', async () => {
    const wrapper = mountBar('POST', 'TAKEN_DOWN', false, true)

    // 会撞唯一键的动作必须禁用（该状态下是「维持下架 / 隐藏联系方式」）
    for (const label of ['维持下架', '隐藏联系方式']) {
      const button = wrapper.findAll('button').find((item) => item.text() === label)
      expect((button?.element as HTMLButtonElement).disabled).toBe(true)
    }
    // 单人即时生效的动作不受影响：恢复可见仍可点
    const restore = wrapper.findAll('button').find((button) => button.text() === '恢复可见')
    expect((restore?.element as HTMLButtonElement).disabled).toBe(false)
    // 并说明为什么点不动，否则用户只会以为按钮坏了
    expect(wrapper.text()).toContain('已有进行中的处置提案')

    // 取消进行中提案后立刻恢复可点
    await wrapper.setProps({ hasOpenProposal: false })
    const enabled = wrapper.findAll('button').find((button) => button.text() === '隐藏联系方式')
    expect((enabled?.element as HTMLButtonElement).disabled).toBe(false)
  })

  it('评论的「移除评论」同样受进行中提案限制，而「举报不成立」不受限', () => {
    const wrapper = mountBar('COMMENT', 'PUBLISHED', false, true)

    const remove = wrapper.findAll('button').find((button) => button.text() === '移除评论')
    const dismiss = wrapper.findAll('button').find((button) => button.text() === '举报不成立')

    expect((remove?.element as HTMLButtonElement).disabled).toBe(true)
    expect((dismiss?.element as HTMLButtonElement).disabled).toBe(false)
  })

  it('有撤销权限时，下架帖给「恢复可见」，联系方式给「恢复联系方式」', () => {
    const result = labels('POST', 'TAKEN_DOWN')

    expect(result).toContain('恢复可见')
    expect(result).toContain('恢复联系方式')
  })

  it('无撤销权限时隐藏两个撤销入口，并说明原因：撤销权归超管', () => {
    const wrapper = mountBar('POST', 'TAKEN_DOWN', false, false, false)
    const result = wrapper.findAll('button').map((button) => button.text())

    // 撤销已作出的处置（恢复可见 / 撤销隐藏）都需要 DISPOSITION_REVOKE
    expect(result).not.toContain('恢复可见')
    expect(result).not.toContain('恢复联系方式')
    // 但「作出处置」的入口仍在（发起提案），否则这一档角色什么都做不了
    expect(result).toContain('维持下架')
    expect(result).toContain('隐藏联系方式')
    // 说清为什么没有按钮，否则会被当成功能缺失
    expect(wrapper.text()).toContain('仅超级管理员可执行')
  })

  it('不传撤销权限时按「无权限」处理（默认安全，漏传只会少给按钮而不是多给）', () => {
    const wrapper = mount(ReviewActionBar, { props: { targetType: 'POST', targetStatus: 'TAKEN_DOWN' } })
    const result = wrapper.findAll('button').map((button) => button.text())

    expect(result).not.toContain('恢复可见')
    expect(result).not.toContain('恢复联系方式')
  })

  it('未获某动作的权限点时不渲染该按钮（与快捷键同口径；默认安全）', () => {
    // 只有「举报不成立」的权限：通过 / 驳回 / 下架 / 隐藏联系方式 等一律不出现
    usePermissions().permissions.value = ['REPORT_DISMISS']

    expect(labels('POST', 'PENDING')).toEqual(['举报不成立'])
  })
})
