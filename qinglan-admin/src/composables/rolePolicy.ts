import type { AssignableRole } from '@/types/admin'
import type { UserRole } from '@shared/types/user'

/**
 * 委员会（管理员 + 学生评议员）合计上限，与后端 {@code UserRole.MAX_COMMITTEE_SIZE} 保持一致。
 *
 * <p>仅用于文案「已用 N / 6」——真正的防超发在后端（带锁计数后判定）。不为此改接口：
 * 让后端为一句文案下发常量并不划算。</p>
 */
export const MAX_COMMITTEE_SIZE = 6

/**
 * 可下放的角色：与后端 {@code AssignableRole} 一致，**刻意不含 SUPER_ADMIN**。
 *
 * <p>标签沿用本页既有措辞：通用角色标签见 {@code useUserLabels().roleLabel}，其中 REVIEWER
 * 显示为「审核员」，与本页的「学生评议员」不同，故不共用，避免悄悄改动这里的文案。</p>
 */
export const ASSIGNABLE_ROLE_OPTIONS: readonly { value: AssignableRole; label: string }[] = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'REVIEWER', label: '学生评议员' },
]

/** 是否属于委员会（参与表决）的角色：与后端 {@code UserRole#isCommittee} 一致 */
export function isCommitteeRole(role: UserRole): boolean {
  return role === 'ADMIN' || role === 'REVIEWER'
}

/**
 * 角色是否可被变更 / 收回：唯一超管不可动。
 * 后端也会拒（{@code SUPER_ADMIN_PROTECTED}），此处只是体验层防线——真正的边界在服务端。
 */
export function isRoleRevocable(role: UserRole): boolean {
  return role !== 'SUPER_ADMIN'
}
