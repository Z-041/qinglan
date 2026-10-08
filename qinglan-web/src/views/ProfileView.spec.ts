import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useUserStore } from '@shared/stores/user'
import ProfileView from '@/views/ProfileView.vue'

/** ProfileView 的右栏入口卡与空态卡都是 RouterLink，用桩替换以免引入路由实例 */
const RouterLinkStub = { template: '<a><slot /></a>' }

describe('ProfileView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('有档案时渲染资料卡与两个右栏卡（含信用分说明）', () => {
    const store = useUserStore()
    store.setProfile({
      id: 1,
      role: 'STUDENT',
      status: 'ACTIVE',
      creditScore: 80,
      bannedUntil: null,
      createdAt: '2026-10-06T00:00:00Z',
    })

    const wrapper = mount(ProfileView, { global: { stubs: { RouterLink: RouterLinkStub } } })

    expect(wrapper.text()).toContain('匿名用户')
    expect(wrapper.text()).toContain('去发布')
    // 阈值取自后端 qinglan.post.min-credit-to-post 默认值 60
    expect(wrapper.text()).toContain('信用分低于 60 无法发帖')
    expect(wrapper.findAll('aside')).toHaveLength(1)
  })
})
