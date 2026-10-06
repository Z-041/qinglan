import { createPinia } from 'pinia'
import { createApp } from 'vue'

import '@unocss/reset/tailwind.css'
import 'virtual:uno.css'
import './styles/main.css'

import App from './App.vue'
import { setUnauthorizedHandler } from './api/http'
import router from './router'
import { useUserStore } from './stores/user'

const pinia = createPinia()
const app = createApp(App).use(pinia).use(router)

// 401 的唯一出口：既清 localStorage（拦截器内做），也重置 Pinia 内存态，否则
// 已失效令牌会继续以「已登录」的身份放行路由守卫与页面（T-12 E）。
const userStore = useUserStore(pinia)
setUnauthorizedHandler(() => {
  userStore.signOut()
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login' })
  }
})

app.mount('#app')
