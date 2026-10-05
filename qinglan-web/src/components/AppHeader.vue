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
    <div class="ql-container flex h-14 items-center gap-3">
      <RouterLink class="flex shrink-0 items-center gap-2" to="/">
        <span
          class="inline-flex size-7 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-primary-deep text-sm font-bold text-white"
        >
          青
        </span>
        <span class="text-base font-semibold tracking-tight text-ink">青栏</span>
      </RouterLink>

      <nav class="flex items-center gap-0.5">
        <RouterLink class="ql-nav-link" to="/">首页</RouterLink>
        <RouterLink v-if="authenticated" class="ql-nav-link" to="/profile">我的</RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-3">
        <template v-if="authenticated">
          <span class="ql-badge hidden bg-primary-soft text-primary-deep sm:inline-flex">{{ roleText }}</span>
          <BaseButton variant="ghost" size="sm" @click="emit('sign-out')">退出</BaseButton>
        </template>
        <RouterLink v-else class="ql-nav-link" to="/login">去认证</RouterLink>
      </div>
    </div>
  </header>
</template>
