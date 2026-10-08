import type { Plugin } from 'vite'

/**
 * 生产构建时把 CSP 注入 index.html。
 *
 * 只在构建期注入：开发期 Vite 以 JS 动态插入 `<style>` 并建立 HMR WebSocket，与严格策略
 * 冲突，故 dev 不变。注意 `frame-ancestors` 写在 `<meta>` 里会被浏览器忽略，防止页面被嵌套
 * 需由托管 index.html 的服务器补 `X-Frame-Options` / `frame-ancestors` 响应头，此处覆盖不到。
 *
 * 放在 shared 而不是各应用里：安全响应头是**不可回退的安全口径**（AGENTS.md 第九章），
 * 学生端与管理端必须一致，各写一份必然漂移。
 *
 * 注意两个应用的 `vite.config.ts` 必须以**相对路径**引用本文件——配置文件由 Vite 自行加载，
 * 不经用户在 `resolve.alias` 里配的别名。
 */
export function contentSecurityPolicy(): Plugin {
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
