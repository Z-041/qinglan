import { createPinia } from 'pinia'
import { createApp } from 'vue'

import '@unocss/reset/tailwind.css'
import 'virtual:uno.css'
// 共享的语义色变量与 body 底色
import '@shared/styles/main.css'
// 管理端专属：Element Plus 主题映射（学生端不加载 EL，故放在本应用）
import './styles/element-plus-vars.css'

import App from './App.vue'
import { setUnauthorizedHandler } from '@shared/api/http'
import { useUserStore } from '@shared/stores/user'
import router from './router'

const pinia = createPinia()
const app = createApp(App).use(pinia).use(router)

// 401 的唯一出口：清持久化（拦截器内做）+ 重置 Pinia 内存态，再回管理端登录页。
// 拆包后本应用只跳自己的登录页，不再需要按路径前缀分叉（那是单应用时代的补丁）。
const userStore = useUserStore(pinia)
setUnauthorizedHandler(() => {
  userStore.signOut()
  const current = router.currentRoute.value
  if (current.name !== 'admin-login') {
    void router.replace({ name: 'admin-login', query: { redirect: current.fullPath } })
  }
})

// 不注册 428（强制阅读）处理器：428 由「发帖」触发，管理端不发帖。
// 拦截器在无处理器时会退化为 toast，不会静默吞掉（见 shared/src/api/http.ts）。

app.mount('#app')
