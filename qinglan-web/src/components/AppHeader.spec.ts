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
})
