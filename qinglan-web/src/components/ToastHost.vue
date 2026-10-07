<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts, dismissToast } = useToast()
</script>

<template>
  <!-- z-toast 取 3000，必须高于 Element Plus 的弹层（其 defaultInitialZIndex 为 2000）：
       管理端弹窗内的请求失败提示只有压在弹层之上才看得见、点得掉。分层口径见 uno.config.ts -->
  <div class="pointer-events-none fixed inset-x-0 top-4 z-toast flex flex-col items-center gap-2 px-4">
    <TransitionGroup name="toast">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        type="button"
        role="alert"
        class="pointer-events-auto max-w-sm cursor-pointer rounded-lg px-4 py-2.5 text-sm shadow-lg max-sm:max-w-[92vw]"
        :class="toast.type === 'error' ? 'bg-danger-soft text-danger-deep' : 'bg-surface text-ink'"
        @click="dismissToast(toast.id)"
      >
        {{ toast.message }}
      </button>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
}
</style>
