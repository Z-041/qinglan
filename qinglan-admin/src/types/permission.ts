/**
 * 管理端权限点，与后端 `common/security/AdminPermission` 一一对应（P5 设计 4.1）。
 *
 * 前端只按权限点渲染菜单与按钮；**隐藏只是体验层**，真正的防线在每个端点的
 * `@PreAuthorize` 上（与「脱敏在服务端完成」同一条原则）。
 *
 * 权限点由 `GET /api/admin/me` 下发，本文件不维护「角色 → 权限点」的映射——
 * 那份映射只在后端一处（`UserRole#permissions()`），前端再抄一份必然漂移。
 */
export type AdminPermission =
  | 'MODERATION_VIEW'
  | 'POST_REVIEW'
  | 'REPORT_DISMISS'
  | 'APPEAL_REVIEW'
  | 'PROPOSAL_CREATE'
  | 'PROPOSAL_VOTE'
  | 'USER_MANAGE'
  | 'ANNOUNCEMENT_DRAFT'
  | 'ANNOUNCEMENT_MANAGE'
  | 'STATS_VIEW'
  | 'ROLE_GRANT'
  | 'PROPOSAL_ARBITRATE'
  | 'AUDIT_READ'

/** 是否拥有某个权限点。判定收敛在此，避免各处写 `permissions.includes(...)` */
export function hasPermission(
  permissions: readonly AdminPermission[],
  required: AdminPermission,
): boolean {
  return permissions.includes(required)
}
