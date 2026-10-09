<script setup lang="ts">
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminPendingPostItem } from '@/types/review'

const props = defineProps<{
  items: AdminPendingPostItem[]
  loading?: boolean
  failed?: boolean
  /** 键盘行焦点所在行的键（postId 字符串）；用于高亮当前处置目标 */
  activeKey?: string | null
}>()

const emit = defineEmits<{ handle: [item: AdminPendingPostItem] }>()

const { targetStatusLabel } = useUserLabels()
</script>

<template>
  <table class="w-full border-collapse text-sm">
    <thead class="sticky top-0 z-10 bg-surface">
      <tr class="border-b border-line text-left text-xs text-muted">
        <th class="px-3 py-2.5 font-medium">内容摘要</th>
        <th class="px-3 py-2.5 font-medium">图片</th>
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
        :class="props.activeKey === String(row.postId) ? 'bg-primary-soft' : ''"
      >
        <td class="max-w-[420px] truncate px-3 py-3" :title="row.preview">{{ row.preview }}</td>
        <td class="px-3 py-3">
          <!-- 缩略图：放行前必须看得见图，否则图片随帖直接对外可见。
               点开在新标签页看原图；URL 只做 :src/:href 绑定，不拼 HTML -->
          <div v-if="row.images.length" class="flex flex-wrap gap-1">
            <a
              v-for="url in row.images"
              :key="url"
              :href="url"
              target="_blank"
              rel="noopener noreferrer"
              class="block overflow-hidden rounded border border-line"
            >
              <img :src="url" alt="帖子图片" class="h-12 w-12 object-cover" loading="lazy" />
            </a>
          </div>
          <!-- 无图时给占位，避免空列看起来像渲染坏了 -->
          <span v-else class="text-xs text-muted">—</span>
        </td>
        <td class="px-3 py-3">{{ targetStatusLabel(row.targetStatus) }}</td>
        <td class="px-3 py-3">
          <!-- 新帖通常为 0；非 0 说明该帖同时被举报，先看举报队列也能处置 -->
          <span v-if="row.reportCount > 0" class="font-medium text-danger-deep">{{ row.reportCount }}</span>
          <span v-else class="text-muted">0</span>
        </td>
        <td class="px-3 py-3" :title="formatAbsoluteTime(row.submittedAt)">
          {{ formatRelativeTime(row.submittedAt) }}
        </td>
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
        <td colspan="6" class="px-3 py-10 text-center text-sm text-muted">
          {{ props.loading ? '加载中…' : props.failed ? '加载失败，请点击「刷新」重试' : '暂无待审帖子' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
