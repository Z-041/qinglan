<script setup lang="ts">
import BaseButton from '@shared/components/base/BaseButton.vue'
import RelativeTime from '@shared/components/RelativeTime.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminUserItem } from '@/types/admin'

const props = defineProps<{ items: AdminUserItem[]; loading?: boolean; failed?: boolean }>()

const emit = defineEmits<{
  ban: [item: AdminUserItem]
  unban: [item: AdminUserItem]
  credit: [item: AdminUserItem]
}>()

const { roleLabel, statusLabel } = useUserLabels()
</script>

<template>
  <!-- 不渲染 id 列（决策 6 / 不变量 #3）：行定位靠注册时间 + 角色 + 状态 + 信用分，
       操作按钮隐式携带 id -->
  <table class="w-full border-collapse text-sm">
    <thead class="sticky top-0 z-10 bg-surface">
      <tr class="border-b border-line text-left text-xs text-muted">
        <th class="px-3 py-2.5 font-medium">注册时间</th>
        <th class="px-3 py-2.5 font-medium">角色</th>
        <th class="px-3 py-2.5 font-medium">状态</th>
        <th class="px-3 py-2.5 font-medium">信用分</th>
        <th class="px-3 py-2.5 font-medium">封禁至</th>
        <th class="px-3 py-2.5 font-medium">操作</th>
      </tr>
    </thead>
    <tbody>
      <!-- key 用 id 只为稳定复用 DOM，该数字不渲染到界面上 -->
      <tr v-for="row in props.items" :key="row.id" class="border-b border-line last:border-0">
        <td class="px-3 py-3">
          <RelativeTime :iso="row.createdAt" />
        </td>
        <td class="px-3 py-3">{{ roleLabel(row.role) }}</td>
        <td class="px-3 py-3" :class="row.status === 'ACTIVE' ? '' : 'font-medium text-danger-deep'">
          {{ statusLabel(row.status) }}
        </td>
        <td class="px-3 py-3 tabular-nums">{{ row.creditScore }}</td>
        <!-- 封禁至指向未来时刻，用绝对时间；相对时间会把未来归为「刚刚」，反而误导 -->
        <td class="px-3 py-3">
          <RelativeTime v-if="row.bannedUntil" variant="absolute-short" :iso="row.bannedUntil" />
          <span v-else>—</span>
        </td>
        <td class="px-3 py-3">
          <div class="flex gap-2">
            <BaseButton v-if="row.status === 'ACTIVE'" variant="ghost-danger" size="xs" @click="emit('ban', row)">
              封禁
            </BaseButton>
            <BaseButton v-else variant="ghost" size="xs" @click="emit('unban', row)">
              解封
            </BaseButton>
            <BaseButton variant="ghost" size="xs" @click="emit('credit', row)">
              调分
            </BaseButton>
          </div>
        </td>
      </tr>
      <tr v-if="!props.items.length">
        <td colspan="6" class="px-3 py-10 text-center text-sm text-muted">
          {{ props.loading ? '加载中…' : props.failed ? '加载失败，请点击「刷新」重试' : '没有符合条件的用户' }}
        </td>
      </tr>
    </tbody>
  </table>
</template>
