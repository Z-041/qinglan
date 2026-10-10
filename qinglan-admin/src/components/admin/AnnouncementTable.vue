<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import DataTable from '@/components/admin/DataTable.vue'
import type { TableStateProps } from '@/components/admin/tableProps'
import { formatAbsoluteTime } from '@shared/composables/useRelativeTime'
import type { AdminAnnouncementItem, AnnouncementLevel, AnnouncementStatus } from '@/types/admin'

const props = withDefaults(
  defineProps<
    TableStateProps<AdminAnnouncementItem> & {
      /**
       * 当前用户是否可执行发布/撤回。
       * 两者后端都只放行 `SUPER_ADMIN`（P4 决策 D8），故共用一个开关；
       * 普通管理员只能建草稿，按钮置灰并由页面给出说明。
       */
      canOperate?: boolean
      /** 发布/撤回请求在途：期间禁用行内按钮，避免连点发出两次请求 */
      submitting?: boolean
    }
  >(),
  { loading: false, canOperate: false, failed: false, submitting: false },
)

const emit = defineEmits<{
  publish: [item: AdminAnnouncementItem]
  revoke: [item: AdminAnnouncementItem]
}>()

/** 后端下发英文枚举，界面只展示中文标签 */
const LEVEL_LABELS: Record<AnnouncementLevel, string> = {
  URGENT: '紧急',
  NORMAL: '普通',
  ACTIVITY: '活动',
}

const STATUS_LABELS: Record<AnnouncementStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  REVOKED: '已撤回',
}

function levelLabel(level: AnnouncementLevel): string {
  return LEVEL_LABELS[level] ?? '未知'
}

function statusLabel(status: AnnouncementStatus): string {
  return STATUS_LABELS[status] ?? '未知'
}

/** 生效区间是「窗口」而非单一时刻，用绝对时间（锁定 zh-CN，不随浏览器语言漂移） */
function dateText(value: string | null): string {
  return value ? formatAbsoluteTime(value) : '—'
}

function periodText(item: AdminAnnouncementItem): string {
  return `${dateText(item.startAt)} ~ ${item.endAt ? dateText(item.endAt) : '长期'}`
}

function boolText(value: boolean): string {
  return value ? '是' : '否'
}
</script>

<template>
  <DataTable
    :items="props.items"
    :colspan="9"
    :loading="props.loading"
    :failed="props.failed"
    empty-text="还没有公告"
  >
    <template #head>
      <th class="px-3 py-2.5 font-medium">标题</th>
      <th class="px-3 py-2.5 font-medium">等级</th>
      <th class="px-3 py-2.5 font-medium">状态</th>
      <th class="px-3 py-2.5 font-medium">生效区间</th>
      <th class="px-3 py-2.5 font-medium">最短阅读秒数</th>
      <th class="px-3 py-2.5 font-medium">需滚动到底</th>
      <th class="px-3 py-2.5 font-medium">需勾选已了解</th>
      <th class="px-3 py-2.5 font-medium">发布时间</th>
      <th class="px-3 py-2.5 font-medium">操作</th>
    </template>

    <!-- key 用 id 只为稳定复用 DOM，该数字不渲染到界面上 -->
    <tr v-for="row in props.items" :key="row.id" class="border-b border-line last:border-0">
      <td class="px-3 py-3">{{ row.title }}</td>
      <td class="px-3 py-3" :class="row.level === 'URGENT' ? 'font-medium text-danger-deep' : ''">
        {{ levelLabel(row.level) }}
      </td>
      <td class="px-3 py-3">{{ statusLabel(row.status) }}</td>
      <td class="whitespace-nowrap px-3 py-3">{{ periodText(row) }}</td>
      <td class="px-3 py-3 tabular-nums">{{ row.minReadSeconds }}</td>
      <td class="px-3 py-3">{{ boolText(row.requireScrollToEnd) }}</td>
      <td class="px-3 py-3">{{ boolText(row.requireConfirm) }}</td>
      <td class="whitespace-nowrap px-3 py-3">
        <RelativeTime v-if="row.publishedAt" :iso="row.publishedAt" />
        <span v-else>—</span>
      </td>
      <td class="px-3 py-3">
        <div class="flex gap-2">
          <BaseButton
            v-if="row.status === 'DRAFT'"
            variant="ghost"
            size="xs"
            :disabled="!props.canOperate || props.submitting"
            @click="emit('publish', row)"
          >
            发布
          </BaseButton>
          <BaseButton
            v-else-if="row.status === 'PUBLISHED'"
            variant="ghost-danger"
            size="xs"
            :disabled="!props.canOperate || props.submitting"
            @click="emit('revoke', row)"
          >
            撤回
          </BaseButton>
          <span v-else class="text-xs text-muted">—</span>
        </div>
      </td>
    </tr>
  </DataTable>
</template>
