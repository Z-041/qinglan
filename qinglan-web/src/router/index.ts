import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '@shared/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录后才能访问 */
    requiresAuth?: boolean
    /** 已登录用户不应再访问（如认证页） */
    guestOnly?: boolean
    /**
     * 宽页面：该页内容容器用 `ql-shell`（1200px），因此顶部导航也要跟着放宽到同一宽度。
     * `App.vue` 的 `fullWidth` 同时驱动头部容器与 main 容器，二者必须一致，否则左边缘错位。
     */
    wide?: boolean
  }
}

/**
 * 学生端路由。
 *
 * 管理端已独立为 `qinglan-admin`（部署在同源 /admin/ 子路径下），故这里**不再有 /admin 子树
 * 与 `requiresAdmin`**——否则学生端会捕获 /admin 路径。管理员账号仍可通过学生端登录，
 * 但后端权限校验与后台页面都在管理端应用内。
 */
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
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && userStore.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

export default router
