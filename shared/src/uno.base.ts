/**
 * 共享的 UnoCSS 基础配置（学生端与管理端同源）。
 *
 * 放在 shared 而不是某个应用里的原因：语义色与 `ql-*` 快捷类是全站唯一口径，
 * 两端各写一份必然会漂移。各应用在自己的 `uno.config.ts` 里展开这里的三项，
 * 并补上自己的 `content` 扫描范围（两个应用的扫描范围不同，故不在此处合并）。
 *
 * 约定（见 AGENTS.md 第七章）：
 * - 布局/间距/排版一律用原子类，颜色只用此处的语义色，禁止在模板里写死十六进制
 * - 语义色统一指向 src/styles/main.css 的 CSS 变量：变量是全站唯一色值来源
 * - 语义色是 CSS 变量，**不能用透明度修饰符**（如 `bg-primary/10`）；
 *   需要浅色底时用专门的 `*-soft` / `*-tint` 语义色
 *   （内置色如 `white` 不受此限，可写 `bg-white/10` 做装饰）
 * - 小字号文字与对比度：浅色底（primary-soft / primary-tint）上的文字统一用 `text-primary-deep`；
 *   「填充底 + 白字」的组件一律用 `bg-primary-deep`（悬停 `bg-primary-strong`）。
 *   `bg-primary` / `text-primary` 只用于装饰、大色块与大字号（详见 styles/main.css 的色值注释）
 * - 危险色同此分档：`text-danger-deep` 压 `bg-danger-soft`，`bg-danger` 承载白字（悬停 `bg-danger-deep`）
 */

export const theme = {
  colors: {
    primary: 'var(--ql-primary)',
    'primary-deep': 'var(--ql-primary-deep)',
    'primary-strong': 'var(--ql-primary-strong)',
    'primary-soft': 'var(--ql-primary-soft)',
    'primary-tint': 'var(--ql-primary-tint)',
    canvas: 'var(--ql-canvas)',
    surface: 'var(--ql-surface)',
    'surface-glass': 'var(--ql-surface-glass)',
    line: 'var(--ql-line)',
    'line-strong': 'var(--ql-line-strong)',
    ink: 'var(--ql-ink)',
    muted: 'var(--ql-muted)',
    danger: 'var(--ql-danger)',
    'danger-deep': 'var(--ql-danger-deep)',
    'danger-soft': 'var(--ql-danger-soft)',
    warning: 'var(--ql-warning)',
    'warning-soft': 'var(--ql-warning-soft)',
  },
  /**
   * z 轴分层：全站唯一口径，优先级从低到高
   *   页头 header 50 < 学生端弹层 overlay 900 < 强制阅读公告 announcement 950
   *   < **Element Plus 弹层与遮罩 2000+** < 全局提示 toast 3000
   *
   * Element Plus 的 `defaultInitialZIndex` 是 2000（每开一个新弹层递增），所以全局提示
   * **必须高于 2000**：否则管理端在 el-dialog 里发起请求失败时，错误提示会被遮罩压在下面
   * ——既看不清也点不掉，而按 AGENTS.md 第七章的错误唯一出口约定，页面不会内联渲染这段文案。
   */
  zIndex: {
    header: '50',
    overlay: '900',
    announcement: '950',
    toast: '3000',
  },
}

export const shortcuts = {
  // 容器：窄屏满宽限宽居中（发布/详情/我的/登录四个页面沿用）
  'ql-container': 'mx-auto w-full max-w-[720px] px-4',

  // PC 为主的学生端首页容器：默认即 1200px 宽（PC 基准），
  // 窄屏（<1024px）回退到与 ql-container 等价的窄容器，观感与此前一致
  'ql-shell': 'mx-auto w-full max-w-[1200px] px-6 max-lg:(max-w-[720px] px-4)',

  // 卡片
  'ql-card': 'rounded-xl border border-line bg-surface shadow-sm',
  'ql-card-interactive':
    'ql-card relative transition duration-200 hover:-translate-y-0.5 hover:border-primary-soft hover:shadow-md',

  // 圆形小标签（角色、状态等）
  'ql-badge': 'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',

  // 渐变标题文字（需显式补 -webkit- 前缀，否则部分浏览器不生效）
  'ql-title-gradient':
    'bg-gradient-to-r from-primary to-primary-deep bg-clip-text text-transparent [-webkit-background-clip:text]',

  // 顶部导航链接（依赖 vue-router 自动附加的 router-link-exact-active）
  'ql-nav-link':
    'rounded-lg px-2.5 py-1.5 text-sm text-muted transition hover:bg-primary-tint hover:text-ink [&.router-link-exact-active]:bg-primary-soft [&.router-link-exact-active]:font-medium [&.router-link-exact-active]:text-primary-deep',

  // 表单
  'ql-label': 'mb-1.5 block text-xs text-muted',
  'ql-field':
    'w-full rounded-lg border border-line bg-surface px-3 py-2.5 text-sm text-ink outline-none transition placeholder:text-muted focus:(border-primary ring-2 ring-primary-soft)',
  'ql-error': 'mt-1.5 block text-xs text-danger',
  'ql-hint': 'mt-1.5 block text-xs text-muted',

  // 按钮基类（配色变体由 BaseButton 组件给出，避免与组件重复维护）
  'ql-btn':
    'inline-flex items-center justify-center gap-1.5 rounded-lg text-sm font-medium transition select-none disabled:(cursor-not-allowed opacity-60)',
  'ql-btn-md': 'px-4 py-2.5',
  'ql-btn-sm': 'px-3 py-1.5',
}

export const preflights = [
  {
    getCSS: () => `
      html {
        -webkit-text-size-adjust: 100%;
      }
      body {
        background-color: var(--ql-canvas);
        /* 顶部极淡的主色光晕，给纯色画布一点纵深 */
        background-image: radial-gradient(circle at 50% -10%, var(--ql-primary-tint), transparent 55%);
        background-repeat: no-repeat;
        color: var(--ql-ink);
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
      }
    `,
  },
]
