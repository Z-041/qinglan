<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AnnouncementModal from '@/components/AnnouncementModal.vue'
import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@shared/components/ToastHost.vue'
import { useAnnouncement } from '@/composables/useAnnouncement'
import { useNotifications } from '@/composables/useNotifications'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@shared/stores/user'

const userStore = useUserStore()
const { profile } = storeToRefs(userStore)
const notificationStore = useNotificationStore()
const { unreadCount } = storeToRefs(notificationStore)
const { refreshUnread } = useNotifications()
const { refreshPending, reset: resetAnnouncements, clearSkippedAnnouncements } = useAnnouncement()
const route = useRoute()
const router = useRouter()

const authenticated = computed(() => userStore.isAuthenticated)

/**
 * 内容容器是否放宽：由路由 meta.wide 声明
 * （首页/详情/发布/我的用 ql-shell = 1200px，登录页与消息中心仍是 ql-container）。
 *
 * 头部与 main 的宽度**必须由同一个判断驱动**：头部容器 1200px 而内容容器 720px 时，
 * 1440px 视口下头部 logo 左边缘在 x=144、内容左边缘在 x=376，两者错开 232px。
 *
 * 拆分后这里不再需要排除 /admin 子树——管理端已独立成应用。
 */
const fullWidth = computed(() => route.meta.wide === true)

/**
 * 前台退出登录。
 *
 * 必须显式导航：路由守卫只在**导航时**运行，只清登录态而不跳转的话，用户会停在一个
 * 已经不可用的页面上——`/profile` 会只剩标题（档案已清空，三个分支都不命中），
 * `/publish` 会停在一张提交不了的表单上。
 *
 * 去向分两种：需要认证的页面回首页；游客本就能浏览的页面（首页、详情）留在原地，
 * 由 `authenticated` 驱动自动切成游客态，不打断浏览。
 */
function handleSignOut(): void {
  userStore.signOut()
  if (route.meta.requiresAuth) {
    void router.replace('/')
  }
}

/**
 * 登录态变化必须重新拉待读公告。原因：游客按 `device_id` 判已读、账号按 `user_id` 判，
 * 两者是不同维度——游客在登录页读过的公告，登录后账号维度仍是「未读」，不重拉就会漏掉强制阅读。
 * 退出时清掉公告状态，避免把上一个身份的弹窗留给下一个人。
 */
watch(
  authenticated,
  (value) => {
    if (value) {
      void refreshUnread()
    } else {
      notificationStore.reset()
      resetAnnouncements()
    }
    // 换了身份要重新评估待读公告：上一个身份「本次会话已放行」的记录不适用于新身份
    clearSkippedAnnouncements()
    // 游客也要被强制阅读，故两种身份都要拉（refreshPending 内部不会覆盖正在展示的那条）
    void refreshPending()
  },
  { immediate: true },
)

/**
 * 进入任意页面都拉一次待读公告（设计方案 5.5「进入任意页面请求 pending」）。
 * 拆分后管理端不再经过本组件，`resetAnnouncements()` 的分支随之删除。
 */
watch(
  () => route.path,
  () => {
    void refreshPending()
  },
)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader
      :authenticated="authenticated"
      :role="profile?.role ?? null"
      :wide="fullWidth"
      :unread-count="unreadCount"
      @sign-out="handleSignOut"
    />

    <main :class="fullWidth ? 'flex-1' : 'ql-container flex-1 py-10 max-sm:py-6'">
      <!-- 按 path 强制重建：Vue Router 对「同一路由只换参数」默认复用组件实例，
           于是 /posts/1 直接改成 /posts/2 时 setup 不会重跑、页面仍显示旧帖。
           按 path 加 key 让参数变化等同一次重新进入，代价只是这类跳转多做一次挂载。 -->
      <RouterView :key="route.path" />
    </main>

    <footer class="border-t border-line bg-surface">
      <p class="ql-container py-5 text-center text-xs text-muted">青栏 · 只面向本校认证学生的匿名社区</p>
    </footer>

    <AnnouncementModal />
    <ToastHost />
  </div>
</template>
