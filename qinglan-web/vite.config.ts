import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import type { Plugin } from 'vite'
import { defineConfig } from 'vitest/config'

/**
 * 生产构建时把 CSP 注入 index.html。
 *
 * 只在构建期注入：开发期 Vite 以 JS 动态插入 <style> 并建立 HMR WebSocket，与严格策略
 * 冲突，故 dev 不变。注意 frame-ancestors 写在 <meta> 里会被浏览器忽略，防止页面被嵌套
 * 需由托管 index.html 的服务器补 X-Frame-Options / frame-ancestors 响应头，此处覆盖不到。
 */
function contentSecurityPolicy(): Plugin {
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    // UnoCSS 产物是外链 CSS；保留 'unsafe-inline' 兼容运行时注入的少量 <style>
    "style-src 'self' 'unsafe-inline'",
    // data:/blob: 当前未被用到（图片恒为 /uploads 同源地址），是留给客户端本地预览的：
    // 将来用 FileReader / URL.createObjectURL 做上传前预览时需要，故不按死配置清理
    "img-src 'self' data: blob:",
    "font-src 'self'",
    // 接口默认同源（/api 由反向代理转发）；若部署成跨域 API，必须在此补上其 origin
    "connect-src 'self'",
    "base-uri 'none'",
    "object-src 'none'",
    "form-action 'self'",
  ].join('; ')

  return {
    name: 'qinglan-csp',
    apply: 'build',
    transformIndexHtml() {
      return [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: policy },
          injectTo: 'head-prepend',
        },
      ]
    },
  }
}

export default defineConfig({
  plugins: [
    UnoCSS(),
    vue(),
    // Element Plus 按需自动导入：只有 admin 视图实际用到的组件与样式会进 admin 懒加载
    // chunk，学生端首屏 bundle 不受影响（spec D4）。
    // dts 必须落在 src/ 下，才能被 tsconfig 的 include（src/**/*.ts）覆盖；
    // 落到项目根目录会让 vue-tsc 找不到 ElMessage / ElMessageBox 这些自动导入符号。
    AutoImport({
      // 只为 ElMessage / ElMessageBox 这类「函数式调用」的组件提供导入与样式注入；
      // vue / vue-router 仍显式 import，与既有代码风格保持一致
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
