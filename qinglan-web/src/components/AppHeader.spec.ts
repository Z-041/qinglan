import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppHeader from './AppHeader.vue'

const routerLinkStub = {
  template: '<a><slot /></a>',
}

describe('AppHeader', () => {
  it('未认证时展示去认证入口且不展示退出按钮', () => {
    const wrapper = mount(AppHeader, {
      props: { authenticated: false, role: null },
      global: { stubs: { RouterLink: routerLinkStub } },
    })

    expect(wrapper.text()).toContain('去认证')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('已认证时展示中文角色标签，不泄漏后端枚举', async () => {
    const wrapper = mount(AppHeader, {
      props: { authenticated: true, role: 'STUDENT' },
      global: { stubs: { RouterLink: routerLinkStub } },
    })

    expect(wrapper.text()).toContain('学生')
    expect(wrapper.text()).not.toContain('STUDENT')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('sign-out')).toHaveLength(1)
  })

  it('头部容器宽度跟随页面：默认 720px，wide 时 1200px', () => {
    // 头部与 main 的容器必须一致，否则 1440px 视口下两者左边缘会错开 232px
    const narrow = mount(AppHeader, {
      props: { authenticated: false, role: null },
      global: { stubs: { RouterLink: routerLinkStub } },
    })
    expect(narrow.get('header > div').classes()).toContain('ql-container')

    const wide = mount(AppHeader, {
      props: { authenticated: false, role: null, wide: true },
      global: { stubs: { RouterLink: routerLinkStub } },
    })
    expect(wide.get('header > div').classes()).toContain('ql-shell')
  })

  it('已认证时展示未读角标，未读数透传给铃铛', () => {
    const wrapper = mount(AppHeader, {
      props: { authenticated: true, role: 'STUDENT', unreadCount: 2 },
      global: { stubs: { RouterLink: routerLinkStub } },
    })

    expect(wrapper.text()).toContain('2')
    // 铃铛入口用 RouterLink，不新增 button：退出仍是唯一 button
    expect(wrapper.findAll('button')).toHaveLength(1)
  })
})
