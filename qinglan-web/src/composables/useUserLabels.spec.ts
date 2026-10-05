import { describe, expect, it } from 'vitest'

import { useUserLabels } from '@/composables/useUserLabels'

const { roleLabel, statusLabel } = useUserLabels()

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
})
