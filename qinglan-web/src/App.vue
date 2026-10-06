<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const { profile } = storeToRefs(userStore)
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
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader
      v-if="!isAdminArea"
      :authenticated="authenticated"
      :role="profile?.role ?? null"
      :wide="fullWidth"
      @sign-out="userStore.signOut()"
    />

    <main :class="fullWidth ? 'flex-1' : 'ql-container flex-1 py-6 sm:py-10'">
      <RouterView />
    </main>

    <footer v-if="!isAdminArea" class="border-t border-line bg-surface">
      <p class="ql-container py-5 text-center text-xs text-muted">青栏 · 只面向本校认证学生的匿名社区</p>
    </footer>

    <ToastHost />
  </div>
</template>
