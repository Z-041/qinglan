import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import App from '@/App.vue'
import { setAuthToken } from '@shared/api/http'
import { useUserStore } from '@/stores/user'
import type { UserProfile } from '@shared/types/user'

// App 挂载即会拉待读公告与未读计数；这两处不是本用例关注点，给桩避免真发请求
vi.mock('@/api/announcement', () => ({
  fetchPendingAnnouncement: vi.fn().mockResolvedValue(null),
  markAnnouncementRead: vi.fn(),
}))

vi.mock('@/api/notification', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn().mockResolvedValue({ count: 0 }),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const ADMIN: UserProfile = {
  id: 1,
  role: 'ADMIN',
  status: 'ACTIVE',
  creditScore: 100,
  bannedUntil: null,
  createdAt: '2026-10-01T00:00:00Z',
}

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>home</div>' } },
      { path: '/posts/:id', name: 'post-detail', component: { template: '<div>detail</div>' } },
      {
        path: '/profile',
        name: 'profile',
        component: { template: '<div>profile</div>' },
        meta: { requiresAuth: true },
      },
      { path: '/publish', name: 'publish', component: { template: '<div>publish</div>' } },
      {
        path: '/notifications',
        name: 'notifications',
        component: { template: '<div>notifications</div>' },
      },
      { path: '/login', name: 'login', component: { template: '<div>login</div>' } },
    ],
  })
}

async function mountAppAt(router: Router, pinia: Pinia, path: string) {
  await router.push(path)
  await router.isReady()
  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
      stubs: { AnnouncementModal: true, ToastHost: true },
    },
  })
  await flushPromises()
  return wrapper
}

function signOutButton(wrapper: Awaited<ReturnType<typeof mountAppAt>>) {
  const button = wrapper.findAll('button').find((item) => item.text() === '退出')
  if (!button) {
    throw new Error('找不到「退出」按钮')
  }
  return button
}

describe('App 前台退出登录', () => {
  let pinia: Pinia

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    localStorage.clear()
    // 令牌以 api/http 的模块级内存态为准，不会自动复位，必须显式清掉
    setAuthToken(null)
    vi.clearAllMocks()
  })

  it('在需要认证的页面上退出后跳回首页，不停在已不可用的页面上', async () => {
    const router = makeRouter()
    const userStore = useUserStore(pinia)
    userStore.setAccessToken('token')
    userStore.setProfile(ADMIN)

    const wrapper = await mountAppAt(router, pinia, '/profile')
    await signOutButton(wrapper).trigger('click')
    await flushPromises()

    expect(userStore.isAuthenticated).toBe(false)
    // 关键断言：不跳转的话会停在只剩标题的空白 /profile
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('在游客本就能浏览的页面上退出后留在原地，不打断浏览', async () => {
    const router = makeRouter()
    const userStore = useUserStore(pinia)
    userStore.setAccessToken('token')
    userStore.setProfile(ADMIN)

    const wrapper = await mountAppAt(router, pinia, '/posts/9')
    await signOutButton(wrapper).trigger('click')
    await flushPromises()

    expect(userStore.isAuthenticated).toBe(false)
    expect(router.currentRoute.value.path).toBe('/posts/9')
  })
})
