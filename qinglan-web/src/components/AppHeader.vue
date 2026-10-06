<script setup lang="ts">
import { computed } from 'vue'

import BaseButton from '@/components/base/BaseButton.vue'
import { useUserLabels } from '@/composables/useUserLabels'
import type { UserRole } from '@/types/user'

const props = defineProps<{
  authenticated: boolean
  role: UserRole | null
}>()

const { roleLabel } = useUserLabels()

/** 后端下发的是英文枚举，导航栏只展示中文标签 */
const roleText = computed(() => (props.role ? roleLabel(props.role) : ''))

const emit = defineEmits<{ 'sign-out': [] }>()
</script>

<template>
  <header class="sticky top-0 z-50 border-b border-line bg-surface-glass backdrop-blur">
    <div class="ql-shell flex h-16 items-center gap-3 max-sm:h-14">
      <RouterLink class="flex shrink-0 items-center gap-2.5" to="/">
        <span
          class="inline-flex size-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary-deep text-lg font-bold text-white max-sm:size-9 max-sm:text-base"
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
          <span class="ql-badge hidden bg-primary-soft text-primary-deep sm:inline-flex">{{ roleText }}</span>
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
