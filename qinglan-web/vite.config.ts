import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import { defineConfig } from 'vitest/config'

// 配置文件由 Vite 自行加载、不经自定义 resolve.alias，故对 shared 只能用相对路径
import { contentSecurityPolicy } from '../shared/src/vite/cspPlugin'

export default defineConfig({
  plugins: [UnoCSS(), vue(), contentSecurityPolicy()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // 共享包以源码形式引入。必须与 tsconfig.json 的 paths 同步，否则会出现
      // 「vite build 通过、vue-tsc 报红」这类难查的症状（见 前后台拆分实施计划.md §5.2）
      '@shared': fileURLToPath(new URL('../shared/src', import.meta.url)),
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
    // 拆分后学生端不再依赖 Element Plus，原先为解析其 .css 而设的
    // `server.deps.inline: ['element-plus']` 已删除（element-plus 由 qinglan-admin 安装）
  },
})
