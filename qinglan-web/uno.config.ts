import { fileURLToPath, URL } from 'node:url'

import { defineConfig, presetWind3, transformerDirectives, transformerVariantGroup } from 'unocss'

// 这里刻意用**相对路径**而非 `@shared/uno.base`：本文件由 UnoCSS 经 jiti 加载，
// 不在 Vite 的模块解析链路里，`vite.config.ts` 与 `tsconfig.json` 配的 `@shared` 别名都不生效，
// 用别名会以「找不到模块」在构建期炸掉。其余源码走 Vite/vitest/tsc，别名正常可用。
import { preflights, shortcuts, theme } from '../shared/src/uno.base'

/**
 * 学生端 UnoCSS 配置。
 *
 * 语义色 / `ql-*` 快捷类 / z 轴分层都下沉在 `@shared/uno.base`（两端唯一口径），
 * 这里只补本应用自己的 `content` 扫描范围。
 *
 * **`content.pipeline.include` 必须含 `shared/src`**：UnoCSS 默认只扫 vite root，
 * 而共享组件（BaseButton/BaseInput/FormField…）在 root 之外。漏了这一条，
 * 那些组件用到的类与 `ql-btn`/`ql-field` 都不会生成——单测仍全绿（断言的是类名），
 * 但页面样式全崩。核对方式：构建产物里 grep `.ql-btn` / `.ql-field`。
 */
const sharedSrc = fileURLToPath(new URL('../shared/src', import.meta.url))

export default defineConfig({
  presets: [presetWind3()],
  transformers: [transformerDirectives(), transformerVariantGroup()],
  // `theme` 由 shared 提供。这里断言成 object 是刻意的：`defineConfig<T extends object = Theme>`
  // 会拿 `theme` 的字面量类型反推泛型，而 `presetWind3()` 返回 `Preset<Theme>`，两者一旦同时成为
  // 候选就会冲突（TS2322）。断言后泛型退回默认的 Theme，预设重新匹配；主题结构的产物由构建期核对。
  theme: theme as object,
  shortcuts,
  preflights,
  content: {
    pipeline: {
      include: [/\.[jt]sx?$/, /\.vue$/, `${sharedSrc}/**/*.{ts,vue}`],
    },
  },
})
