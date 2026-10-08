import { describe, expect, it } from 'vitest'

import router from '@/router'
import { NAV_ITEMS, visibleNavItems } from '@/router/navItems'
import type { AdminPermission } from '@/types/permission'

/** 与后端 UserRole.permissions() 对应的三个集合（测试里显式列出，避免「用实现测实现」） */
const REVIEWER: AdminPermission[] = [
  'MODERATION_VIEW',
  'POST_REVIEW',
  'REPORT_DISMISS',
  'PROPOSAL_CREATE',
  'PROPOSAL_VOTE',
  'STATS_VIEW',
]
const ADMIN: AdminPermission[] = [...REVIEWER, 'APPEAL_REVIEW', 'USER_MANAGE', 'ANNOUNCEMENT_DRAFT']
const SUPER_ADMIN: AdminPermission[] = [
  ...ADMIN,
  'ANNOUNCEMENT_MANAGE',
  'ROLE_GRANT',
  'PROPOSAL_ARBITRATE',
  'AUDIT_READ',
]

function labels(permissions: AdminPermission[]): string[] {
  return visibleNavItems(permissions).map((item) => item.label)
}

describe('visibleNavItems', () => {
  it('没有任何权限点时不显示任何菜单（学生账号被放进后台也看不到东西）', () => {
    expect(visibleNavItems([])).toEqual([])
  })

  it('评议员：只能看到治理相关的四页，看不到申诉 / 用户 / 公告 / 权限 / 审计', () => {
    expect(labels(REVIEWER)).toEqual(['看板', '待审帖子', '审核队列', '我的提案与投票'])
  })

  it('管理员：多出申诉复审、用户管理、公告管理，但仍看不到权限管理与审计日志', () => {
    expect(labels(ADMIN)).toEqual([
      '看板',
      '待审帖子',
      '审核队列',
      '我的提案与投票',
      '申诉复审',
      '用户管理',
      '公告管理',
    ])
  })

  it('超管：十项菜单全部可见', () => {
    expect(labels(SUPER_ADMIN)).toEqual([
      '看板',
      '待审帖子',
      '审核队列',
      '我的提案与投票',
      '申诉复审',
      '用户管理',
      '公告管理',
      '权限管理',
      '待裁决',
      '审计日志',
    ])
  })

  it('菜单顺序稳定：不因权限点顺序变化而改变', () => {
    const reversed = [...SUPER_ADMIN].reverse()
    expect(labels(reversed)).toEqual(labels(SUPER_ADMIN))
  })

  it('菜单名唯一且每项都声明了权限点（两个菜单指向同一路由、或漏声明权限都会在这里红）', () => {
    const names = NAV_ITEMS.map((item) => item.name)
    expect(new Set(names).size).toBe(names.length)
    for (const item of NAV_ITEMS) {
      // 注意：权限点**可以**被多个菜单共用——待审帖子与审核队列都用 MODERATION_VIEW，
      // 它们同属「治理数据的读」，拆成两个点只会多一处漏配的机会
      expect(item.permission).toBeTruthy()
      expect(item.label).toBeTruthy()
    }
  })

  it('菜单里的路由名必须真实存在（防「菜单点了跳 404」这类漂移）', () => {
    const routeNames = new Set(router.getRoutes().map((route) => route.name))
    for (const item of NAV_ITEMS) {
      expect(routeNames).toContain(item.name)
    }
  })
})
