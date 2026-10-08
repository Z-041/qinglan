import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * shared 只跑自己的单测（http、useToast、useUserLabels、useAuth 与 base 组件），
 * 不产出构建产物——它由两个应用以源码形式引入。
 *
 * vue 插件必须有：base 组件的 spec 直接 import .vue，缺它 vitest 解析不了 SFC。
 * 不需要 UnoCSS 插件：单测断言的是类名而非最终样式（样式是否生成在两端产物里核对）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@shared': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
  },
})
