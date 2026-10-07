import { createRouter, createWebHistory } from 'vue-router'

import { useAuth } from '@/composables/useAuth'
import { pushToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/user'
import type { UserRole } from '@/types/user'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录后才能访问 */
    requiresAuth?: boolean
    /** 已登录用户不应再访问（如认证页） */
    guestOnly?: boolean
    /**
     * 需要管理员角色（ADMIN / SUPER_ADMIN）。
     * 未登录 → 管理端登录页；已登录但非管理员 → 提示后回首页。
     */
    requiresAdmin?: boolean
    /**
     * 宽页面：该页内容容器用 `ql-shell`（1200px），因此顶部导航也要跟着放宽到同一宽度。
     * `App.vue` 的 `fullWidth` 同时驱动头部容器与 main 容器，二者必须一致，否则左边缘错位。
     */
    wide?: boolean
  }
}

/** 管理后台的准入角色（spec D3：REVIEWER 本轮不放行） */
const ADMIN_ROLES: UserRole[] = ['ADMIN', 'SUPER_ADMIN']

function isAdmin(role: UserRole | null | undefined): boolean {
  return role !== null && role !== undefined && ADMIN_ROLES.includes(role)
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { wide: true },
    },
    {
      // 只接受数字 id：否则 /posts/abc 会被视图 Number() 成 NaN 再拿去请求接口
      path: '/posts/:id(\\d+)',
      name: 'post-detail',
      component: () => import('@/views/PostDetailView.vue'),
      meta: { wide: true },
    },
    {
      path: '/publish',
      name: 'publish',
      component: () => import('@/views/PublishView.vue'),
      meta: { requiresAuth: true, wide: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true, wide: true },
    },
    {
      // 消息中心是窄内容页（ql-container），故不设 meta.wide
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // 刻意不设 guestOnly：与学生端 /login 同设会让两页互相弹走（spec 5.3 重定向环防护）
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      meta: { requiresAdmin: true },
      children: [
        { path: '', redirect: { name: 'admin-dashboard' } },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/DashboardView.vue'),
        },
        {
          path: 'review-queue',
          name: 'admin-review-queue',
          component: () => import('@/views/admin/ReviewQueueView.vue'),
        },
        {
          path: 'appeals',
          name: 'admin-appeals',
          component: () => import('@/views/admin/AppealQueueView.vue'),
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/UserManageView.vue'),
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: () => import('@/views/admin/AnnouncementManageView.vue'),
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin) {
    if (!userStore.isAuthenticated) {
      return { name: 'admin-login', query: { redirect: to.fullPath } }
    }
    // 刷新页面后 profile 已不在内存里，先按令牌拉一次再判定，否则会把管理员误判成普通用户
    if (!userStore.profile) {
      try {
        await useAuth().loadProfile()
      } catch {
        // 拿不到档案（多为 401，也可能网络故障）：此刻**无法确认**管理员身份，
        // 应回管理端登录页并带上原目标，而不是把已持令牌的用户丢回首页——
        // 那会让令牌失效的管理员以为「后台入口没了」，也覆盖掉拦截器已发出的跳转。
        return { name: 'admin-login', query: { redirect: to.fullPath } }
      }
    }
    if (!isAdmin(userStore.profile?.role)) {
      pushToast('无权访问管理后台', 'error')
      return { name: 'home' }
    }
  }

  if (to.meta.guestOnly && userStore.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

export default router
