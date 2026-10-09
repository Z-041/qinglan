<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useUserLabels } from '@shared/composables/useUserLabels'
import { useUserStore } from '@shared/stores/user'
import TodoBell from '@/components/admin/TodoBell.vue'
import { usePermissions } from '@/composables/usePermissions'
import { buildTodoGroups, useTodoCounts, type TodoGroup } from '@/composables/useTodoCounts'
import { visibleNavItems } from '@/router/navItems'

const router = useRouter()
const userStore = useUserStore()
const { profile } = storeToRefs(userStore)
const { roleLabel } = useUserLabels()
const { permissions, reset } = usePermissions()

/**
 * 菜单按权限点过滤（P5 设计 9.1）。
 *
 * 不在这里写 `role === 'SUPER_ADMIN'` 这类判断：菜单与权限的对应关系在 `navItems.ts`
 * 一处维护，角色差异只体现在后端下发的权限点上。
 */
const navItems = computed(() => visibleNavItems(permissions.value))

const roleText = computed(() => (profile.value ? roleLabel(profile.value.role) : ''))

/** 顶栏待办铃铛：30 秒轮询待办计数，分组按权限点裁剪（无权限的组后端不下发、前端也不渲染） */
const { counts, clear } = useTodoCounts()
const todoGroups = computed(() => buildTodoGroups(counts.value, permissions.value))

/** 点击分组：跳对应页面并本地清零该组计数（下一次轮询仍会拉回真实值） */
function openTodo(group: TodoGroup): void {
  clear(group.key)
  void router.push({ name: group.routeName })
}

function signOut(): void {
  userStore.signOut()
  // 权限点是模块级状态，换账号必须清掉，否则上一份权限会漏给下一个人
  reset()
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
          v-for="item in navItems"
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
        <div class="flex items-center gap-3">
          <TodoBell :groups="todoGroups" @select="openTodo" />
          <button
            type="button"
            class="cursor-pointer text-sm text-muted transition hover:text-danger-deep"
            @click="signOut"
          >
            退出登录
          </button>
        </div>
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
