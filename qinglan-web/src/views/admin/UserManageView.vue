<script setup lang="ts">
import { onMounted, ref } from 'vue'

import BanUserDialog from '@/components/admin/BanUserDialog.vue'
import CreditAdjustDialog from '@/components/admin/CreditAdjustDialog.vue'
import UserTable from '@/components/admin/UserTable.vue'
import BaseButton from '@/components/base/BaseButton.vue'
import { useUserLabels } from '@/composables/useUserLabels'
import { useUserManage } from '@/composables/useUserManage'
import type { AdminUserItem } from '@/types/admin'
import type { UserRole, UserStatus } from '@/types/user'

const {
  filters,
  items,
  loading,
  loadingMore,
  hasMore,
  submitting,
  refresh,
  loadMore,
  reset,
  ban,
  unban,
  adjustCredit,
} = useUserManage()

const { roleLabel, statusLabel } = useUserLabels()

const STATUSES: UserStatus[] = ['ACTIVE', 'BANNED', 'BLACKLISTED']
const ROLES: UserRole[] = ['STUDENT', 'REVIEWER', 'ADMIN', 'SUPER_ADMIN']

/** 当前被操作的行；对话框/确认框只认它，不额外存 id */
const selected = ref<AdminUserItem | null>(null)
const banOpen = ref(false)
const creditOpen = ref(false)

onMounted(() => {
  void refresh()
})

function openBan(row: AdminUserItem): void {
  selected.value = row
  banOpen.value = true
}

function openCredit(row: AdminUserItem): void {
  selected.value = row
  creditOpen.value = true
}

/** 成功才关对话框；失败保持打开，错误由拦截器提示（同 Task 15） */
async function handleBan(days: number, reason: string): Promise<void> {
  if (selected.value && (await ban(selected.value.id, { days, reason }))) {
    banOpen.value = false
    await refresh()
  }
}

async function handleCredit(delta: number, reason: string): Promise<void> {
  if (selected.value && (await adjustCredit(selected.value.id, { delta, reason }))) {
    creditOpen.value = false
    await refresh()
  }
}

async function handleUnban(row: AdminUserItem): Promise<void> {
  try {
    const result = await ElMessageBox.prompt('请填写解封理由，会写入审计日志', '解除封禁', {
      confirmButtonText: '确认解封',
      cancelButtonText: '取消',
      inputPlaceholder: '理由',
      inputValidator: (value: string) => (value.trim().length > 0 ? true : '理由不能为空'),
    })
    if (await unban(row.id, { reason: result.value.trim() })) {
      await refresh()
    }
  } catch {
    // 用户取消：ElMessageBox 以 reject 表示，不是错误，静默返回
  }
}
</script>

<template>
  <div class="flex flex-col gap-5">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">用户管理</h1>
      <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">刷新</BaseButton>
    </div>

    <form class="ql-card flex flex-wrap items-end gap-3 p-4" @submit.prevent="refresh">
      <label class="flex flex-col gap-1">
        <span class="ql-label">状态</span>
        <select v-model="filters.status" class="ql-field">
          <option value="">不限</option>
          <option v-for="status in STATUSES" :key="status" :value="status">
            {{ statusLabel(status) }}
          </option>
        </select>
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">角色</span>
        <select v-model="filters.role" class="ql-field">
          <option value="">不限</option>
          <option v-for="role in ROLES" :key="role" :value="role">{{ roleLabel(role) }}</option>
        </select>
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">信用分下界</span>
        <input v-model="filters.minCredit" type="number" class="ql-field" placeholder="含" />
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">信用分上界</span>
        <input v-model="filters.maxCredit" type="number" class="ql-field" placeholder="含" />
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">注册时间起</span>
        <input v-model="filters.createdFrom" type="datetime-local" class="ql-field" />
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">注册时间止</span>
        <input v-model="filters.createdTo" type="datetime-local" class="ql-field" />
      </label>

      <div class="flex gap-2">
        <BaseButton html-type="submit" size="sm" :loading="loading">查询</BaseButton>
        <BaseButton variant="ghost" size="sm" @click="reset">重置</BaseButton>
      </div>
    </form>

    <section class="ql-card overflow-hidden">
      <UserTable
        :items="items"
        :loading="loading"
        @ban="openBan"
        @unban="handleUnban"
        @credit="openCredit"
      />

      <div v-if="hasMore" class="border-t border-line px-3 py-3 text-center">
        <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
          加载更多
        </BaseButton>
      </div>
    </section>

    <BanUserDialog
      :open="banOpen"
      :submitting="submitting"
      @submit="handleBan"
      @close="banOpen = false"
    />
    <CreditAdjustDialog
      :open="creditOpen"
      :submitting="submitting"
      @submit="handleCredit"
      @close="creditOpen = false"
    />
  </div>
</template>
