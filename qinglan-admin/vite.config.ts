import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vitest/config'

// 配置文件由 Vite 自行加载、不经自定义 resolve.alias，故对 shared 只能用相对路径
import { contentSecurityPolicy } from '../shared/src/vite/cspPlugin'

export default defineConfig({
  // 管理端与前端同源部署在 /admin/ 子路径下（同源才能共用 localStorage 里的令牌，
  // 维持 AGENTS.md 第七章「一个浏览器 = 一个账号 = 一份会话」的约定）。
  // 必须与 router 的 createWebHistory('/admin/') 一致，否则 /admin 下会白屏或刷新 404。
  base: '/admin/',
  plugins: [
    UnoCSS(),
    vue(),
    // Element Plus 按需自动导入：只有本应用实际用到的组件与样式会进产物。
    // dts 必须落在 src/ 下，才能被 tsconfig 的 include 覆盖；
    // 落到项目根目录会让 vue-tsc 找不到 ElMessage / ElMessageBox 这些自动导入符号。
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
    contentSecurityPolicy(),
  ],
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
    // element-plus 组件（如 el-dialog）经 unplugin-vue-components 自动导入时会连带注入样式入口，
    // 而 Vitest 默认把 node_modules 依赖外置交给 Node 加载，Node 解析不了 .css，会在收集阶段
    // 直接抛 Unknown file extension ".css"。把 element-plus 交给 Vite 处理即可正常解析样式。
    server: {
      deps: {
        inline: ['element-plus'],
      },
    },
  },
})
