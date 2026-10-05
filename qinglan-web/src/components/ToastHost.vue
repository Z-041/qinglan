<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts, dismissToast } = useToast()
</script>

<template>
  <div class="pointer-events-none fixed inset-x-0 top-4 z-[1000] flex flex-col items-center gap-2 px-4">
    <TransitionGroup name="toast">
      <button
        v-for="toast in toasts"
        :key="toast.id"
        type="button"
        role="alert"
        class="pointer-events-auto max-w-[92vw] cursor-pointer rounded-lg px-4 py-2.5 text-sm shadow-lg sm:max-w-sm"
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
