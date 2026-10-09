import { describe, expect, it } from 'vitest'

import {
  ASSIGNABLE_ROLE_OPTIONS,
  isCommitteeRole,
  isRoleRevocable,
  MAX_COMMITTEE_SIZE,
} from '@/composables/rolePolicy'
import type { UserRole } from '@shared/types/user'

describe('rolePolicy', () => {
  it('委员会上限与后端 UserRole.MAX_COMMITTEE_SIZE 一致', () => {
    expect(MAX_COMMITTEE_SIZE).toBe(6)
  })

  it('可下放角色不含超管，且只有管理员与评议员', () => {
    expect(ASSIGNABLE_ROLE_OPTIONS.map((option) => option.value)).toEqual(['ADMIN', 'REVIEWER'])
  })

  it('委员会角色判定：仅管理员与评议员', () => {
    const committee: UserRole[] = ['ADMIN', 'REVIEWER']
    const others: UserRole[] = ['STUDENT', 'SUPER_ADMIN']
    committee.forEach((role) => expect(isCommitteeRole(role)).toBe(true))
    others.forEach((role) => expect(isCommitteeRole(role)).toBe(false))
  })

  it('角色可收回判定：唯一超管不可动，其余可动', () => {
    expect(isRoleRevocable('SUPER_ADMIN')).toBe(false)
    const revocable: UserRole[] = ['STUDENT', 'REVIEWER', 'ADMIN']
    revocable.forEach((role) => expect(isRoleRevocable(role)).toBe(true))
  })
})
