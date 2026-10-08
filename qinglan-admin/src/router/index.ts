import { createRouter, createWebHistory } from 'vue-router'

import { useAuth } from '@shared/composables/useAuth'
import { pushToast } from '@shared/composables/useToast'
import { useUserStore } from '@shared/stores/user'
import { isAdminRole } from '@shared/types/user'
import { usePermissions } from '@/composables/usePermissions'
import type { AdminPermission } from '@/types/permission'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要管理员角色（ADMIN / SUPER_ADMIN）；学生评议员也在此列 */
    requiresAdmin?: boolean
    /**
     * 该页面所需的权限点（P5）。菜单已按权限过滤，这里兜的是「手输 URL」这条路——
     * 前端拦一道只是体验，真正的防线是后端端点上的注解。
     */
    permission?: AdminPermission
  }
}

const router = createRouter({
  // base 与 vite.config.ts 的 `base: '/admin/'` 必须一致；
  // 路由表内部写 /login、/dashboard，最终 URL 仍是 /admin/login、/admin/dashboard，
  // 与拆分前完全一致（现有链接与书签不失效）。
  history: createWebHistory('/admin/'),
  routes: [
    {
      // 刻意不设「已登录就弹走」的守卫：与学生端 /login 互设会造成重定向环（spec 5.3）
      path: '/login',
      name: 'admin-login',
      component: () => import('@/views/AdminLoginView.vue'),
    },
    {
      path: '/',
      component: () => import('@/views/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      children: [
        { path: '', redirect: { name: 'admin-dashboard' } },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { permission: 'STATS_VIEW' },
        },
        {
          path: 'pending-posts',
          name: 'admin-pending-posts',
          component: () => import('@/views/PendingPostQueueView.vue'),
          meta: { permission: 'MODERATION_VIEW' },
        },
        {
          path: 'review-queue',
          name: 'admin-review-queue',
          component: () => import('@/views/ReviewQueueView.vue'),
          meta: { permission: 'MODERATION_VIEW' },
        },
        {
          path: 'appeals',
          name: 'admin-appeals',
          component: () => import('@/views/AppealQueueView.vue'),
          meta: { permission: 'APPEAL_REVIEW' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/UserManageView.vue'),
          meta: { permission: 'USER_MANAGE' },
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: () => import('@/views/AnnouncementManageView.vue'),
          meta: { permission: 'ANNOUNCEMENT_DRAFT' },
        },
        {
          path: 'permissions',
          name: 'admin-permissions',
          component: () => import('@/views/PermissionManageView.vue'),
          meta: { permission: 'ROLE_GRANT' },
        },
        {
          path: 'audit-logs',
          name: 'admin-audit-logs',
          component: () => import('@/views/AuditLogView.vue'),
          meta: { permission: 'AUDIT_READ' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAdmin) {
    return true
  }
  const userStore = useUserStore()

  if (!userStore.isAuthenticated) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  // 刷新页面后 profile 已不在内存里，先按令牌拉一次再判定，否则会把管理员误判成普通用户
  if (!userStore.profile) {
    try {
      await useAuth().loadProfile()
    } catch {
      // 拿不到档案（多为 401，也可能网络故障）：此刻**无法确认**管理员身份，
      // 回登录页并带上原目标，而不是把已持令牌的用户丢掉
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
  }
  if (!isAdminRole(userStore.profile?.role)) {
    // 学生账号可能被用来试后台：清干净登录态，不能把令牌留在本地
    userStore.signOut()
    pushToast('无权访问管理后台', 'error')
    return { name: 'admin-login' }
  }

  // P5：页面级权限点。菜单已按权限过滤，这里兜的是「手输 URL / 旧书签」这条路
  const permissionState = usePermissions()
  if (!permissionState.loaded.value) {
    try {
      await permissionState.load()
    } catch {
      // 拿不到权限点就无法判断能进哪个页面：清掉身份回登录页，
      // 而不是把一个「什么都点不动」的空壳后台放进去
      permissionState.reset()
      userStore.signOut()
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
  }
  const required = to.meta.permission
  if (required && !permissionState.can(required)) {
    pushToast('当前账号没有该页面的权限', 'error')
    return { name: 'admin-dashboard' }
  }
  return true
})

export default router
