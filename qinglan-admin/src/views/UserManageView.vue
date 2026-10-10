<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import BanUserDialog from '@/components/admin/BanUserDialog.vue'
import CreditAdjustDialog from '@/components/admin/CreditAdjustDialog.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import UserTable from '@/components/admin/UserTable.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { exportAdminUsers } from '@/api/admin'
import { useCsvExport } from '@/composables/useCsvExport'
import { toQuery, useUserManage } from '@/composables/useUserManage'
import type { AdminUserItem } from '@/types/admin'
import { ALL_ROLES, type UserStatus } from '@shared/types/user'

const {
  filters,
  items,
  loading,
  loadingMore,
  hasMore,
  failed,
  filtersDirty,
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
const ROLES = ALL_ROLES

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

/**
 * filters 的信用分字段声明为 string，type="number" 上的 v-model 会写入 number，
 * 使 toNumberOrNull 的 value.trim() 抛错（异常被 refresh 的 catch 吞掉，表现为点查询无反应）。
 */
function setCreditFilter(key: 'minCredit' | 'maxCredit', event: Event): void {
  filters.value[key] = (event.target as HTMLInputElement).value
}

/** 空串=不限；填了必须是 0-100 的整数（后端也会校验，这里前置拦住，免得多弹一次 400） */
function creditBoundInvalid(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) {
    return false
  }
  const parsed = Number(trimmed)
  return !Number.isInteger(parsed) || parsed < 0 || parsed > 100
}

const creditRangeInvalid = computed(
  () => creditBoundInvalid(filters.value.minCredit) || creditBoundInvalid(filters.value.maxCredit),
)

/** 查询与刷新的统一入口：信用分越界时不发请求 */
async function search(): Promise<void> {
  if (creditRangeInvalid.value) {
    return
  }
  await refresh()
}

const { exporting, download } = useCsvExport()

/** 按**当前查询条件**导出（无游标，导出完整结果集）；信用分越界时不发请求 */
async function exportCsv(): Promise<void> {
  if (creditRangeInvalid.value) {
    return
  }
  await download(() => exportAdminUsers(toQuery(filters.value, null)), 'users')
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
    <PageHeader :refreshing="loading" title="用户管理" @refresh="search">
      <template #extra>
        <BaseButton
          variant="ghost"
          size="sm"
          :loading="exporting"
          :disabled="creditRangeInvalid"
          @click="exportCsv"
        >
          导出 CSV
        </BaseButton>
      </template>
    </PageHeader>

    <form class="ql-card flex flex-wrap items-end gap-3 p-4" @submit.prevent="search">
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
        <input
          :value="filters.minCredit"
          type="number"
          min="0"
          max="100"
          class="ql-field"
          placeholder="含"
          @input="setCreditFilter('minCredit', $event)"
        />
        <span v-if="creditBoundInvalid(filters.minCredit)" class="ql-error">须为 0-100 的整数</span>
      </label>

      <label class="flex flex-col gap-1">
        <span class="ql-label">信用分上界</span>
        <input
          :value="filters.maxCredit"
          type="number"
          min="0"
          max="100"
          class="ql-field"
          placeholder="含"
          @input="setCreditFilter('maxCredit', $event)"
        />
        <span v-if="creditBoundInvalid(filters.maxCredit)" class="ql-error">须为 0-100 的整数</span>
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
        <BaseButton
          html-type="submit"
          size="sm"
          :loading="loading"
          :disabled="creditRangeInvalid"
        >
          查询
        </BaseButton>
        <BaseButton variant="ghost" size="sm" @click="reset">重置</BaseButton>
      </div>
    </form>

    <section class="ql-card overflow-clip">
      <UserTable
        :items="items"
        :loading="loading"
        :failed="failed"
        @ban="openBan"
        @unban="handleUnban"
        @credit="openCredit"
      />

      <LoadMoreButton
        variant="table"
        :has-more="hasMore"
        :loading="loadingMore"
        :disabled="filtersDirty"
        :hint="filtersDirty ? '筛选条件已修改，请先点击「查询」再继续加载' : ''"
        @load="loadMore"
      />
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
