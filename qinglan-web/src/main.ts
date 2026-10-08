import { createPinia } from 'pinia'
import { createApp } from 'vue'

import '@unocss/reset/tailwind.css'
import 'virtual:uno.css'
import '@shared/styles/main.css'

import App from './App.vue'
import { setPreconditionRequiredHandler, setUnauthorizedHandler } from '@shared/api/http'
import { openFromPrecondition } from './composables/useAnnouncement'
import router from './router'
import { useUserStore } from '@shared/stores/user'
import type { PendingAnnouncement } from './types/announcement'

const pinia = createPinia()
const app = createApp(App).use(pinia).use(router)

// 401 的唯一出口：既清 localStorage（拦截器内做），也重置 Pinia 内存态，否则
// 已失效令牌会继续以「已登录」的身份放行路由守卫与页面（T-12 E）。
const userStore = useUserStore(pinia)
setUnauthorizedHandler(() => {
  userStore.signOut()
  const current = router.currentRoute.value
  // 管理端与学生端各有一个登录页：按路径前缀分叉，且只在不是本区登录页时才跳，
  // 避免两个登录页互相跳转（spec 5.3 重定向环防护）
  const adminArea = current.path.startsWith('/admin')
  const loginName = adminArea ? 'admin-login' : 'login'
  if (current.name !== loginName) {
    void router.replace({
      name: loginName,
      query: adminArea ? { redirect: current.fullPath } : {},
    })
  }
})

// 428（存在未读紧急公告）的唯一出口：直接用手上这条公告开弹窗，不再多发一次请求（计划 D15）。
setPreconditionRequiredHandler((data) => {
  openFromPrecondition(data as PendingAnnouncement)
})

app.mount('#app')
