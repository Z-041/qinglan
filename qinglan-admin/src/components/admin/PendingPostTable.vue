<script setup lang="ts">
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminPendingPostItem } from '@/types/review'

const props = defineProps<{ items: AdminPendingPostItem[]; loading?: boolean; failed?: boolean }>()

const emit = defineEmits<{ handle: [item: AdminPendingPostItem] }>()

const { targetStatusLabel } = useUserLabels()
</script>

<template>
  <table class="w-full border-collapse text-sm">
    <thead>
      <tr class="border-b border-line text-left text-xs text-muted">
        <th class="px-3 py-2.5 font-medium">内容摘要</th>
        <th class="px-3 py-2.5 font-medium">状态</th>
        <th class="px-3 py-2.5 font-medium">待处理举报</th>
        <th class="px-3 py-2.5 font-medium">提交时间</th>
        <th class="px-3 py-2.5 font-medium">操作</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in props.items"
        :key="row.postId"
        class="border-b border-line last:border-0"
      >
        <td class="max-w-[420px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
        <td class="px-3 py-3">{{ targetStatusLabel(row.targetStatus) }}</td>
        <td class="px-3 py-3">
          <!-- 新帖通常为 0；非 0 说明该帖同时被举报，先看举报队列也能处置 -->
          <span v-if="row.reportCount > 0" class="font-medium text-danger-deep">{{ row.reportCount }}</span>
          <span v-else class="text-muted">0</span>
        </td>
        <td class="px-3 py-3">{{ new Date(row.submittedAt).toLocaleString() }}</td>
        <td class="px-3 py-3">
          <button
            type="button"
            class="cursor-pointer rounded-lg border border-line px-3 py-1.5 text-xs text-ink transition hover:border-primary hover:text-primary-deep"
            @click="emit('handle', row)"
          >
            审核
          </button>
        </td>
      </tr>
      <tr v-if="!props.items.length">
        <td colspan="5" class="px-3 py-10 text-center text-sm text-muted">
          {{ props.loading ? '加载中…' : props.failed ? '加载失败，请点击「刷新」重试' : '暂无待审帖子' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
