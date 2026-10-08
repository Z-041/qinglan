/**
 * 登录后的回跳目标解析。
 *
 * <p>单独成文件是为了可测：这段判断曾经写成「`redirect` 以 `/admin` 开头才采用」，
 * 而路由守卫传的是**不带 base** 的 fullPath（如 `/users`），于是判断恒为假、回跳被丢弃；
 * fallback 又写成带 base 的 `/admin/dashboard`，被 `createWebHistory('/admin/')` 再拼一次，
 * 实际跳到 `/admin/admin/dashboard`（靠 catch-all 兜回看板）。两个坑都只在浏览器里看得见，
 * 故把规则收敛到一处纯函数并加单测。</p>
 */

/** 管理端默认落点，不带 base */
export const ADMIN_HOME = '/dashboard'

/**
 * 把 `?redirect=` 的原始值解析成安全的站内路径。
 *
 * <p>只接受以单个 `/` 开头的站内绝对路径：`//evil.com` 这类协议相对地址会被浏览器当作
 * 外部域名跳转，必须挡掉（开放重定向）。</p>
 */
export function resolveAdminRedirect(raw: unknown): string {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) {
    return ADMIN_HOME
  }
  return raw
}
