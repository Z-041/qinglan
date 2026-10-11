<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { exportAuditLogs, fetchAuditLogs } from '@/api/admin'
import DataTable from '@/components/admin/DataTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseDateTimeInput from '@/components/base/BaseDateTimeInput.vue'
import BaseSelect from '@/components/base/BaseSelect.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
import FormField from '@shared/components/base/FormField.vue'
import LoadMoreButton from '@shared/components/LoadMoreButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import { useCursorList } from '@shared/composables/useCursorList'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { toInstantOrNull } from '@/composables/datetime'
import { useCsvExport } from '@/composables/useCsvExport'
import {
  AUDIT_ACTION_LABELS as ACTION_LABELS,
  AUDIT_TARGET_TYPE_LABELS as TARGET_TYPE_LABELS,
} from '@/types/admin'
import type { AdminAuditLogItem } from '@/types/admin'

const { reviewReasonText } = useUserLabels()
const { exporting, download } = useCsvExport()

const actionFilter = ref('')
/** 原生 datetime-local 的值（本地时间，形如 2026-10-01T08:00），空串 = 不限 */
const fromFilter = ref('')
const toFilter = ref('')

/** 下拉选项：首项「全部动作」（value=''），其余走审计动作中文映射 */
const ACTION_OPTIONS = [
  { value: '', label: '全部动作' },
  ...Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label })),
]

/** 清空筛选并回到第一页 */
async function resetFilters(): Promise<void> {
  actionFilter.value = ''
  fromFilter.value = ''
  toFilter.value = ''
  await refresh()
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

/** 按**当前筛选条件**导出（与列表同口径）；超限等错误由拦截器提示，这里不重复 */
function exportCsv(): void {
  void download(
    () =>
      exportAuditLogs({
        action: actionFilter.value || null,
        from: toInstantOrNull(fromFilter.value),
        to: toInstantOrNull(toFilter.value),
      }),
    'audit-logs',
  )
}

/**
 * 列表状态机收在 shared useCursorList：筛选条件改动会触发 refresh，与在飞的 loadMore
 * 可能交叉（先发的不一定先到），序号防竞态由它统一负责；取页函数在闭包内读当前筛选。
 */
const { items, loading, loadingMore, hasMore, failed, refresh, loadMore } =
  useCursorList<AdminAuditLogItem>({
    fetchPage: (cursor) =>
      fetchAuditLogs({
        cursor,
        action: actionFilter.value || null,
        from: toInstantOrNull(fromFilter.value),
        to: toInstantOrNull(toFilter.value),
      }),
  })

onMounted(() => {
  void refresh()
})
</script>

<template>
  <div class="flex flex-col gap-5">
    <PageHeader :refreshing="loading" title="审计日志" @refresh="refresh">
      <template #extra>
        <BaseButton variant="ghost" size="sm" :loading="exporting" @click="exportCsv">导出 CSV</BaseButton>
      </template>
    </PageHeader>

    <p class="text-sm text-muted">
      记录全部治理动作，只增不改删；操作人只以 ID 出现，不含学号与邮箱明文。
    </p>

    <form class="ql-card flex flex-wrap items-end gap-3 p-4" @submit.prevent="refresh">
      <FormField label="动作">
        <BaseSelect v-model="actionFilter" class="w-[200px]" :options="ACTION_OPTIONS" aria-label="审计动作" />
      </FormField>

      <FormField label="起始时间">
        <BaseDateTimeInput v-model="fromFilter" class="w-[210px]" aria-label="起始时间" />
      </FormField>

      <FormField label="结束时间">
        <BaseDateTimeInput v-model="toFilter" class="w-[210px]" aria-label="结束时间" />
      </FormField>

      <div class="flex gap-2">
        <BaseButton html-type="submit" size="sm" :loading="loading">查询</BaseButton>
        <BaseButton variant="ghost" size="sm" @click="resetFilters">重置</BaseButton>
      </div>
    </form>

    <section class="ql-card overflow-clip">

      <DataTable
        :items="items"
        :colspan="5"
        :loading="loading"
        :failed="failed"
        @retry="refresh"
        empty-text="暂无审计记录"
      >
        <!-- 审计时间保留绝对时间（合规语义），压成短格式；完整时刻悬停可见 -->
        <template #head>
          <th scope="col" class="ql-th">时间</th>
          <th scope="col" class="ql-th">动作</th>
          <th scope="col" class="ql-th">目标</th>
          <th scope="col" class="ql-th">操作人</th>
          <th scope="col" class="ql-th">理由</th>
        </template>

        <tr v-for="row in items" :key="row.id" class="ql-tr">
          <td class="px-3 py-3 whitespace-nowrap">
            <RelativeTime variant="absolute-short" :iso="row.createdAt" />
          </td>
          <td class="px-3 py-3">{{ actionLabel(row.action) }}</td>
          <td class="px-3 py-3">{{ TARGET_TYPE_LABELS[row.targetType] }} #{{ row.targetId }}</td>
          <td class="px-3 py-3">
            <!-- 0 是系统自动处置的哨兵值（后端 AuditLogService.SYSTEM_OPERATOR_ID） -->
            {{ row.operatorId === 0 ? '系统' : `#${row.operatorId}` }}
          </td>
          <td class="max-w-[320px] truncate px-3 py-3" :title="reviewReasonText(row.reason) ?? ''">
            {{ reviewReasonText(row.reason) ?? '—' }}
          </td>
        </tr>
      </DataTable>

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>
  </div>
</template>
