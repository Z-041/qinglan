import { createPinia } from 'pinia'
import { createApp } from 'vue'

import '@unocss/reset/tailwind.css'
import 'virtual:uno.css'
import './styles/main.css'

import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).mount('#app')
