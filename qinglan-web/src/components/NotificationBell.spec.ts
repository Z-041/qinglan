import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import NotificationBell from './NotificationBell.vue'

/** 入口是 RouterLink，单测用桩替换以免引入路由实例 */
const RouterLinkStub = { template: '<a><slot /></a>' }

function mountBell(unreadCount?: number) {
  return mount(NotificationBell, {
    props: unreadCount === undefined ? {} : { unreadCount },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
}

describe('NotificationBell', () => {
  it('未读数为 0（含默认值）时不显示角标', () => {
    expect(mountBell(0).text()).not.toContain('0')
    expect(mountBell().text().trim()).toBe('')
  })

  it('未读数为 3 时显示 3', () => {
    expect(mountBell(3).text()).toContain('3')
  })

  it('未读数超过 99 时显示 99+', () => {
    expect(mountBell(120).text()).toContain('99+')
  })

  it('组件内不含 button（入口必须是链接，钉住 AppHeader 单 button 不变量）', () => {
    expect(mountBell(5).find('button').exists()).toBe(false)
  })
})
