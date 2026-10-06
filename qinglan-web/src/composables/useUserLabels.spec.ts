import { describe, expect, it } from 'vitest'

import { useUserLabels } from '@/composables/useUserLabels'

const { roleLabel, statusLabel, reportReasonLabel, reviewReasonText } = useUserLabels()

describe('useUserLabels', () => {
  it('角色枚举全部映射为中文标签', () => {
    expect(roleLabel('STUDENT')).toBe('学生')
    expect(roleLabel('REVIEWER')).toBe('审核员')
    expect(roleLabel('ADMIN')).toBe('管理员')
    expect(roleLabel('SUPER_ADMIN')).toBe('超级管理员')
  })

  it('用户状态枚举全部映射为中文标签', () => {
    expect(statusLabel('ACTIVE')).toBe('正常')
    expect(statusLabel('BANNED')).toBe('已封禁')
    expect(statusLabel('BLACKLISTED')).toBe('已拉黑')
  })

  it('举报理由枚举全部映射为中文标签', () => {
    expect(reportReasonLabel('ADVERTISEMENT')).toBe('广告与推广')
    expect(reportReasonLabel('ABUSE')).toBe('辱骂、人身攻击')
    expect(reportReasonLabel('OTHER')).toBe('其他')
  })

  it('下架原因是「违规类型码」时换成中文，裸码不出现在界面上', () => {
    expect(reviewReasonText('PORNOGRAPHY')).toBe('色情低俗')
    expect(reviewReasonText('ABUSE: 人身攻击')).toBe('辱骂、人身攻击：人身攻击')
    expect(reviewReasonText('AUTO_REPORT_THRESHOLD')).toBe('被多次举报，待复核')
  })

  it('未知码不渲染裸码：有说明用说明，无说明给通用文案', () => {
    expect(reviewReasonText('FUTURE_CODE: 新违规')).toBe('新违规')
    expect(reviewReasonText('FUTURE_CODE')).toBe('因违规被处理')
  })

  it('无处置原因时返回 null，供模板判空', () => {
    expect(reviewReasonText(null)).toBeNull()
  })
})
