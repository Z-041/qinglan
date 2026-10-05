import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [UnoCSS(), vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
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
