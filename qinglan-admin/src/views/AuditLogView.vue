<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { exportAuditLogs, fetchAuditLogs } from '@/api/admin'
import DataTable from '@/components/admin/DataTable.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import BaseButton from '@shared/components/base/BaseButton.vue'
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
const fromFilter = ref('')
const toFilter = ref('')

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

    <section class="ql-card overflow-clip">
      <div class="flex flex-wrap items-center gap-2 border-b border-line px-3 py-3">
        <el-select
          v-model="actionFilter"
          class="w-[200px]"
          placeholder="全部动作"
          clearable
          aria-label="按动作筛选"
        >
          <el-option
            v-for="(label, action) in ACTION_LABELS"
            :key="action"
            :label="label"
            :value="action"
          />
        </el-select>
        <el-input
          v-model="fromFilter"
          type="datetime-local"
          class="w-[210px]"
          placeholder="起始时间"
          aria-label="起始时间"
        />
        <span class="text-xs text-muted">至</span>
        <el-input
          v-model="toFilter"
          type="datetime-local"
          class="w-[210px]"
          placeholder="结束时间"
          aria-label="结束时间"
        />
        <BaseButton variant="ghost" size="sm" :loading="loading" @click="refresh">筛选</BaseButton>
      </div>

      <DataTable
        :items="items"
        :colspan="5"
        :loading="loading"
        :failed="failed"
        empty-text="暂无审计记录"
      >
        <!-- 审计时间保留绝对时间（合规语义），压成短格式；完整时刻悬停可见 -->
        <template #head>
          <th class="px-3 py-2.5 font-medium">时间</th>
          <th class="px-3 py-2.5 font-medium">动作</th>
          <th class="px-3 py-2.5 font-medium">目标</th>
          <th class="px-3 py-2.5 font-medium">操作人</th>
          <th class="px-3 py-2.5 font-medium">理由</th>
        </template>

        <tr v-for="row in items" :key="row.id" class="border-b border-line last:border-0">
          <td class="px-3 py-3 whitespace-nowrap">
            <RelativeTime variant="absolute-short" :iso="row.createdAt" />
          </td>
          <td class="px-3 py-3">{{ actionLabel(row.action) }}</td>
          <td class="px-3 py-3">{{ TARGET_TYPE_LABELS[row.targetType] }} #{{ row.targetId }}</td>
          <td class="px-3 py-3">
            <!-- 0 是系统自动处置的哨兵值（后端 AuditLogService.SYSTEM_OPERATOR_ID） -->
            {{ row.operatorId === 0 ? '系统' : `#${row.operatorId}` }}
          </td>
          <td class="max-w-[360px] truncate px-3 py-3" :title="reviewReasonText(row.reason) ?? ''">
            {{ reviewReasonText(row.reason) ?? '—' }}
          </td>
        </tr>
      </DataTable>

      <LoadMoreButton variant="table" :has-more="hasMore" :loading="loadingMore" @load="loadMore" />
    </section>
  </div>
</template>
