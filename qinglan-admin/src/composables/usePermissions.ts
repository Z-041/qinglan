import { ref } from 'vue'

import { fetchAdminMe } from '@/api/admin'
import type { AdminPermission } from '@/types/permission'
import type { UserRole } from '@shared/types/user'

/**
 * 当前账号的管理端权限点（P5 设计 9.1）。
 *
 * 状态放在模块级而不是每次调用各取一份：菜单、路由守卫、按钮读到的必须是**同一份**，
 * 否则会出现「菜单灰了但按钮还在」这类自相矛盾的界面。
 *
 * 与 `useToast` 一样属模块级单例，**测试之间不会自动复位**，spec 必须在 beforeEach 里
 * 调 `reset()`（R-26 的教训）。
 */
const permissions = ref<AdminPermission[]>([])
const role = ref<UserRole | null>(null)
const loaded = ref(false)

export function usePermissions() {
  /** 是否拥有某个权限点。后端仍有对应端点的注解兜底，这里只决定「看不看得到」 */
  function can(required: AdminPermission): boolean {
    return permissions.value.includes(required)
  }

  /** 拉取当前身份。失败时抛给调用方（路由守卫据此回登录页），不在此吞掉 */
  async function load(): Promise<void> {
    const me = await fetchAdminMe()
    role.value = me.role
    permissions.value = me.permissions
    loaded.value = true
  }

  /** 退出登录 / 切换账号时清空，避免上一份权限点漏给下一个账号 */
  function reset(): void {
    permissions.value = []
    role.value = null
    loaded.value = false
  }

  return { permissions, role, loaded, can, load, reset }
}
