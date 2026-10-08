<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useUserLabels } from '@shared/composables/useUserLabels'
import { useUserStore } from '@shared/stores/user'

/** 侧边栏五项（spec 5.4）；name 与路由名一一对应 */
const NAV_ITEMS = [
  { name: 'admin-dashboard', label: '看板' },
  { name: 'admin-review-queue', label: '审核队列' },
  { name: 'admin-appeals', label: '申诉复审' },
  { name: 'admin-users', label: '用户管理' },
  { name: 'admin-announcements', label: '公告管理' },
] as const

const router = useRouter()
const userStore = useUserStore()
const { profile } = storeToRefs(userStore)
const { roleLabel } = useUserLabels()

const roleText = computed(() => (profile.value ? roleLabel(profile.value.role) : ''))

function signOut(): void {
  userStore.signOut()
  void router.replace({ name: 'admin-login' })
}
</script>

<template>
  <!-- el-config-provider 已上提到 App.vue：/login 与本布局是同级路由，放这里登录页拿不到语言包 -->
  <div class="flex min-h-screen bg-canvas">
    <aside class="flex w-[200px] shrink-0 flex-col border-r border-line bg-surface">
      <p class="px-5 py-5 text-sm font-semibold text-ink">青栏管理后台</p>
      <nav class="flex flex-1 flex-col gap-1 px-2">
        <RouterLink
          v-for="item in NAV_ITEMS"
          :key="item.name"
          :to="{ name: item.name }"
          class="rounded-lg px-3 py-2 text-sm text-muted transition hover:bg-primary-tint hover:text-ink [&.router-link-exact-active]:bg-primary-soft [&.router-link-exact-active]:font-medium [&.router-link-exact-active]:text-primary-deep"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-line bg-surface px-6">
        <span class="text-sm text-muted">当前角色：<span class="text-ink">{{ roleText }}</span></span>
        <button
          type="button"
          class="cursor-pointer text-sm text-muted transition hover:text-danger-deep"
          @click="signOut"
        >
          退出登录
        </button>
      </header>

      <!-- 拆分后管理端 App.vue 不再有外层 <main>，这里用 <main> 才是正确语义
           （每个页面应恰有一个主内容地标）。原先为避免嵌套地标才退化成 div。 -->
      <main class="flex-1 p-6">
        <!-- 管理端仅支持 PC，不做移动断点（AGENTS.md 第七章） -->
        <RouterView />
      </main>
    </div>
  </div>
</template>
