import { fileURLToPath, URL } from 'node:url'

import { defineConfig, presetWind3, transformerDirectives, transformerVariantGroup } from 'unocss'

// 相对路径而非 @shared：本文件由 UnoCSS 经 jiti 加载，不在 Vite 的模块解析链路里，
// 别名不生效（详见 前后台拆分实施计划.md 步 2 的记录）。
import { preflights, shortcuts, theme } from '../shared/src/uno.base'

/**
 * 管理端 UnoCSS 配置。
 *
 * 语义色 / `ql-*` 快捷类 / z 轴分层都下沉在 `@shared/uno.base`（两端唯一口径），
 * 这里只补本应用自己的 `content` 扫描范围。
 *
 * `content.pipeline.include` 必须含 `shared/src`：UnoCSS 默认只扫 vite root，
 * 而共享组件（BaseButton/BaseInput/FormField…）在 root 之外，漏配会让 `ql-btn`/`ql-field`
 * 等类不生成——单测仍全绿（断言的是类名），页面样式全崩。
 */
const sharedSrc = fileURLToPath(new URL('../shared/src', import.meta.url))

export default defineConfig({
  presets: [presetWind3()],
  transformers: [transformerDirectives(), transformerVariantGroup()],
  // 断言成 object 是刻意的：defineConfig<T extends object = Theme> 会拿 theme 的字面量类型
  // 反推泛型，与 presetWind3() 的 Preset<Theme> 冲突（TS2322）。
  theme: theme as object,
  shortcuts,
  preflights,
  content: {
    pipeline: {
      include: [/\.[jt]sx?$/, /\.vue$/, `${sharedSrc}/**/*.{ts,vue}`],
    },
  },
})
