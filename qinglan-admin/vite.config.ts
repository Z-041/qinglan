import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { defineConfig } from 'vitest/config'

// 配置文件由 Vite 自行加载、不经自定义 resolve.alias，故对 shared 只能用相对路径
import { contentSecurityPolicy } from '../shared/src/vite/cspPlugin'

export default defineConfig({
  // 管理端与前端同源部署在 /admin/ 子路径下（同源才能共用 localStorage 里的令牌，
  // 维持 AGENTS.md 第七章「一个浏览器 = 一个账号 = 一份会话」的约定）。
  // 必须与 router 的 createWebHistory('/admin/') 一致，否则 /admin 下会白屏或刷新 404。
  base: '/admin/',
  plugins: [UnoCSS(), vue(), contentSecurityPolicy()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // 与 tsconfig.json 的 paths 必须同步，否则会出现「build 通过、vue-tsc 报红」
      '@shared': fileURLToPath(new URL('../shared/src', import.meta.url)),
    },
  },
  server: {
    // 与学生端（5173）错开：两边都默认 5173 会直接冲突
    port: 5174,
    proxy: {
      // 开发期把 /api 透传给本地后端，前端只依赖相对路径，避免硬编码宿主
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 上传的图片由后端静态资源映射直接返回，路径不带 /api 前缀，需单独透传
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
  },
})
