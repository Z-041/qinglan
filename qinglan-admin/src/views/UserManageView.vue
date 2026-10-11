<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import BanUserDialog from '@/components/admin/BanUserDialog.vue'
import CreditAdjustDialog from '@/components/admin/CreditAdjustDialog.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import UserTable from '@/components/admin/UserTable.vue'
import BaseDateTimeInput from '@/components/base/BaseDateTimeInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseInput from '@shared/components/base/BaseInput.vue'
import FormField from '@shared/components/base/FormField.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { exportAdminUsers } from '@/api/admin'
import { useCsvExport } from '@/composables/useCsvExport'
import { useReasonPrompt } from '@/composables/useReasonPrompt'
import { toQuery, useUserManage } from '@/composables/useUserManage'
import type { AdminUserItem } from '@/types/admin'
import { ALL_ROLES, type UserRole, type UserStatus } from '@shared/types/user'

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
/** 解封必须留下理由（会写入审计日志）；弹窗封装见 useReasonPrompt */
const { promptReason } = useReasonPrompt()

const STATUSES: UserStatus[] = ['ACTIVE', 'BANNED', 'BLACKLISTED']
const ROLES = ALL_ROLES

/** 下拉选项：首项为「不限」（value=''），文案走 useUserLabels 的统一中文映射 */
const STATUS_OPTIONS = [
  { value: '', label: '不限' },
  ...STATUSES.map((status) => ({ value: status, label: statusLabel(status) })),
]
const ROLE_OPTIONS = [
  { value: '', label: '不限' },
  ...ROLES.map((role) => ({ value: role, label: roleLabel(role) })),
]

/** 原生 <select> 只认字符串；UserStatus/UserRole 是联合类型，进出各转一次 */
const statusFilter = computed<string>({
  get: () => filters.value.status,
  set: (value) => {
    filters.value.status = value as UserStatus | ''
  },
})
const roleFilter = computed<string>({
  get: () => filters.value.role,
  set: (value) => {
    filters.value.role = value as UserRole | ''
  },
})

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
  const reason = await promptReason('解除封禁', '请填写解封理由，会写入审计日志', '确认解封')
  if (reason === null) {
    return
  }
  if (await unban(row.id, { reason })) {
    await refresh()
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
      <FormField label="状态">
        <BaseSelect v-model="statusFilter" class="w-[140px]" :options="STATUS_OPTIONS" aria-label="状态筛选" />
      </FormField>

      <FormField label="角色">
        <BaseSelect v-model="roleFilter" class="w-[170px]" :options="ROLE_OPTIONS" aria-label="角色筛选" />
      </FormField>

      <FormField
        label="信用分下界"
        :error="creditBoundInvalid(filters.minCredit) ? '须为 0-100 的整数' : ''"
      >
        <BaseInput
          v-model="filters.minCredit"
          class="w-[110px]"
          :invalid="creditBoundInvalid(filters.minCredit)"
          placeholder="含"
          inputmode="numeric"
        />
      </FormField>

      <FormField
        label="信用分上界"
        :error="creditBoundInvalid(filters.maxCredit) ? '须为 0-100 的整数' : ''"
      >
        <BaseInput
          v-model="filters.maxCredit"
          class="w-[110px]"
          :invalid="creditBoundInvalid(filters.maxCredit)"
          placeholder="含"
          inputmode="numeric"
        />
      </FormField>

      <FormField label="注册时间起">
        <BaseDateTimeInput v-model="filters.createdFrom" class="w-[210px]" aria-label="注册时间起" />
      </FormField>

      <FormField label="注册时间止">
        <BaseDateTimeInput v-model="filters.createdTo" class="w-[210px]" aria-label="注册时间止" />
      </FormField>

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
        @retry="refresh"
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
