<script setup lang="ts">
import { computed } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { UserRole } from '@shared/types/user'

const props = withDefaults(
  defineProps<{
    authenticated: boolean
    role: UserRole | null
    /**
     * 页面容器是否放宽（首页 1200px）。头部容器必须与 main 的容器一致，
     * 否则两者左边缘会错开——1440px 视口下 1200 与 720 的差是 232px。
     */
    wide?: boolean
    /** 未读通知数，透传给铃铛；未认证时铃铛不渲染 */
    unreadCount?: number
  }>(),
  { wide: false, unreadCount: 0 },
)

const { roleLabel } = useUserLabels()

/** 后端下发的是英文枚举，导航栏只展示中文标签 */
const roleText = computed(() => (props.role ? roleLabel(props.role) : ''))

const emit = defineEmits<{ 'sign-out': [] }>()
</script>

<template>
  <header class="sticky top-0 z-header border-b border-line bg-surface">
    <div
      class="flex h-16 items-center gap-3 max-sm:h-14"
      :class="props.wide ? 'ql-shell' : 'ql-container'"
    >
      <RouterLink class="flex shrink-0 items-center gap-2.5" to="/">
        <span
          class="inline-flex size-10 items-center justify-center rounded-md bg-primary-deep text-lg font-bold text-white max-sm:size-9 max-sm:text-base"
        >
          青
        </span>
        <span class="flex flex-col leading-tight">
          <span class="text-lg font-bold tracking-tight text-ink">青栏</span>
          <span class="text-xs text-muted max-sm:hidden">本校认证 · 全程匿名</span>
        </span>
      </RouterLink>

      <nav class="flex items-center gap-0.5">
        <RouterLink class="ql-nav-link" to="/">首页</RouterLink>
        <RouterLink v-if="authenticated" class="ql-nav-link" to="/publish">发布</RouterLink>
        <RouterLink v-if="authenticated" class="ql-nav-link" to="/profile">我的</RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <template v-if="authenticated">
          <span class="ql-tag bg-primary-soft text-primary-deep max-sm:hidden">{{ roleText }}</span>
          <NotificationBell :unread-count="props.unreadCount" />
          <BaseButton variant="ghost" size="sm" @click="emit('sign-out')">退出</BaseButton>
        </template>
        <RouterLink
          v-else
          class="ql-btn ql-btn-sm bg-primary-deep text-white shadow-sm hover:bg-primary-strong"
          to="/login"
        >
          去认证
        </RouterLink>
      </div>
    </div>
  </header>
</template>
