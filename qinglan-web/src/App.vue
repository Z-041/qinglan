<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'

import AppHeader from '@/components/AppHeader.vue'
import ToastHost from '@/components/ToastHost.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const { profile } = storeToRefs(userStore)

const authenticated = computed(() => userStore.isAuthenticated)
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader :authenticated="authenticated" :role="profile?.role ?? null" @sign-out="userStore.signOut()" />

    <main class="ql-container flex-1 py-6 sm:py-10">
      <RouterView />
    </main>

    <footer class="border-t border-line bg-surface">
      <p class="ql-container py-5 text-center text-xs text-muted">青栏 · 只面向本校认证学生的匿名社区</p>
    </footer>

    <ToastHost />
  </div>
</template>
