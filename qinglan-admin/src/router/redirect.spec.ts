import { describe, expect, it } from 'vitest'

import { ADMIN_HOME, resolveAdminRedirect } from '@/router/redirect'

describe('resolveAdminRedirect', () => {
  it('采用站内相对路径（守卫传的是不带 base 的 fullPath）', () => {
    expect(resolveAdminRedirect('/users')).toBe('/users')
    expect(resolveAdminRedirect('/review-queue')).toBe('/review-queue')
  })

  it('缺省或非字符串时回落到看板（不带 base）', () => {
    expect(resolveAdminRedirect(undefined)).toBe(ADMIN_HOME)
    expect(resolveAdminRedirect(null)).toBe(ADMIN_HOME)
    expect(resolveAdminRedirect('')).toBe(ADMIN_HOME)
    // 数组形式（?redirect=a&redirect=b）同样不接受
    expect(resolveAdminRedirect(['/users'])).toBe(ADMIN_HOME)
  })

  it('挡掉开放重定向：协议相对地址与站外绝对地址一律不使用', () => {
    expect(resolveAdminRedirect('//evil.example.com')).toBe(ADMIN_HOME)
    expect(resolveAdminRedirect('https://evil.example.com')).toBe(ADMIN_HOME)
    expect(resolveAdminRedirect('users')).toBe(ADMIN_HOME)
  })

  it('不把 base 拼进返回值：base 由 createWebHistory 负责，重复拼接会得到 /admin/admin/xxx', () => {
    expect(resolveAdminRedirect('/users')).not.toContain('/admin/')
    expect(ADMIN_HOME).toBe('/dashboard')
  })
})
