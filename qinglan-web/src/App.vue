<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'

import AnnouncementModal from '@/components/AnnouncementModal.vue'
import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useAnnouncement } from '@/composables/useAnnouncement'
import { useNotifications } from '@/composables/useNotifications'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const { profile } = storeToRefs(userStore)
const notificationStore = useNotificationStore()
const { unreadCount } = storeToRefs(notificationStore)
const { refreshUnread } = useNotifications()
const { refreshPending, reset: resetAnnouncements, clearSkippedAnnouncements } = useAnnouncement()
const route = useRoute()

const authenticated = computed(() => userStore.isAuthenticated)

/**
 * 管理端（/admin 子树）自带 AdminLayout：PC 满宽、自带侧边栏，
 * 不能再套学生端的 ql-container（max-w-[720px]）与学生端吸顶栏/页脚，
 * 否则后台页面会被压成 720px 宽并在上方多出一条学生端导航。
 */
const isAdminArea = computed(() => route.path.startsWith('/admin'))

/**
 * 内容容器是否放宽：管理端自带 AdminLayout；其余页面由路由 meta.wide 声明
 * （首页/详情/发布/我的用 ql-shell = 1200px，登录页仍是 720px 的 ql-container）。
 *
 * 头部与 main 的宽度**必须由同一个判断驱动**：头部容器 1200px 而内容容器 720px 时，
 * 1440px 视口下头部 logo 左边缘在 x=144、内容左边缘在 x=376，两者错开 232px（改造前同为 720px）。
 */
const fullWidth = computed(() => isAdminArea.value || route.meta.wide === true)

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
 * 进入任意学生端页面都拉一次待读公告（设计方案 5.5「进入任意页面请求 pending」）。
 * 管理端不渲染公告弹窗（见模板的 `v-if="!isAdminArea"`），故进入管理端时清掉状态，
 * 离开时由本 watcher 再拉，避免出现「弹窗在管理端后台读秒、回到学生端突然弹出」。
 */
watch(
  () => route.path,
  () => {
    if (isAdminArea.value) {
      resetAnnouncements()
      return
    }
    void refreshPending()
  },
)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader
      v-if="!isAdminArea"
      :authenticated="authenticated"
      :role="profile?.role ?? null"
      :wide="fullWidth"
      :unread-count="unreadCount"
      @sign-out="userStore.signOut()"
    />

    <main :class="fullWidth ? 'flex-1' : 'ql-container flex-1 py-10 max-sm:py-6'">
      <!-- 按 path 强制重建：Vue Router 对「同一路由只换参数」默认复用组件实例，
           于是 /posts/1 直接改成 /posts/2 时 setup 不会重跑、页面仍显示旧帖。
           按 path 加 key 让参数变化等同一次重新进入，代价只是这类跳转多做一次挂载。 -->
      <RouterView :key="route.path" />
    </main>

    <footer v-if="!isAdminArea" class="border-t border-line bg-surface">
      <p class="ql-container py-5 text-center text-xs text-muted">青栏 · 只面向本校认证学生的匿名社区</p>
    </footer>

    <AnnouncementModal v-if="!isAdminArea" />
    <ToastHost />
  </div>
</template>
