import { hasPermission, type AdminPermission } from '@/types/permission'

/**
 * 侧边栏条目与它所需的权限点（P5 设计 4.3）。
 *
 * 单独成文件而不是写在 AdminLayout 里：菜单与权限的对应关系是要被测试钉住的规则
 * （「评议员看不到用户管理」这类结论全靠它），写成纯数据 + 纯函数才好断言。
 */
export interface NavItem {
  /** 路由名，与 router 中的 name 一一对应 */
  name: string
  label: string
  permission: AdminPermission
}

export const NAV_ITEMS: readonly NavItem[] = [
  { name: 'admin-dashboard', label: '看板', permission: 'STATS_VIEW' },
  { name: 'admin-pending-posts', label: '待审帖子', permission: 'MODERATION_VIEW' },
  { name: 'admin-review-queue', label: '审核队列', permission: 'MODERATION_VIEW' },
  { name: 'admin-appeals', label: '申诉复审', permission: 'APPEAL_REVIEW' },
  { name: 'admin-users', label: '用户管理', permission: 'USER_MANAGE' },
  { name: 'admin-announcements', label: '公告管理', permission: 'ANNOUNCEMENT_DRAFT' },
  { name: 'admin-permissions', label: '权限管理', permission: 'ROLE_GRANT' },
  { name: 'admin-audit-logs', label: '审计日志', permission: 'AUDIT_READ' },
]

/** 按当前权限点过滤出可见菜单；顺序保持 NAV_ITEMS 的定义顺序 */
export function visibleNavItems(permissions: readonly AdminPermission[]): NavItem[] {
  return NAV_ITEMS.filter((item) => hasPermission(permissions, item.permission))
}
