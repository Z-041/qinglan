<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@shared/components/base/BaseButton.vue'
import { formatAbsoluteTime, formatRelativeTime } from '@shared/composables/useRelativeTime'
import { useNotifications } from '@/composables/useNotifications'
import { useNotificationStore } from '@/stores/notification'

const { items, loading, loadingMore, hasMore, failed, load, loadMore, markRead, markAllRead, refreshUnread } =
  useNotifications()

const { unreadCount } = storeToRefs(useNotificationStore())

onMounted(() => {
  void load()
  void refreshUnread()
})
</script>

<template>
  <!-- 该路由不加 meta.wide，App.vue 的 main 已经套了 ql-container（720px）；这里再加一层会把
       内容宽压到 ~656px 并叠出 32px 内边距，故视图内只留纵向间距 -->
  <div>
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold text-ink">消息</h1>
      <BaseButton variant="ghost" size="sm" :disabled="unreadCount === 0" @click="markAllRead">
        全部已读
      </BaseButton>
    </div>

    <p v-if="loading" class="py-8 text-center text-sm text-muted">加载中…</p>

    <!-- 加载失败与「真的没有消息」必须分成两屏：否则一次网络故障会被说成「还没有消息」。
         这里的文案是**状态说明**，具体错误信息仍只由 http 拦截器的 toast 给出。 -->
    <div
      v-else-if="failed"
      class="mt-5 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">消息没能加载出来</p>
      <BaseButton class="mt-4" variant="ghost" size="sm" @click="load()">重试</BaseButton>
    </div>

    <div
      v-else-if="!items.length"
      class="mt-5 rounded-xl border border-dashed border-line-strong px-5 py-10 text-center"
    >
      <p class="text-sm font-medium text-ink">还没有消息</p>
    </div>

    <ul v-else class="ql-card mt-5 divide-y divide-line overflow-hidden">
      <li v-for="item in items" :key="item.id">
        <!-- 有 link 时整条是导航链接；无 link 时退化为 button——必须是真 button 而非 div@click，
             否则键盘用户无法聚焦、无法回车标记已读。
             `to` 只在有 link 时才是 RouterLink 的 prop，无 link 时会落到 button 上、
             渲染成一个非标准属性，故用 `?? undefined` 交由 Vue 移除该属性；type 同理。 -->
        <component
          :is="item.link ? RouterLink : 'button'"
          :to="item.link ?? undefined"
          :type="item.link ? undefined : 'button'"
          class="flex w-full items-start gap-3 bg-transparent px-4 py-3.5 text-left transition hover:bg-primary-tint"
          :class="item.readAt ? '' : 'bg-primary-tint'"
          @click="markRead(item.id)"
        >
          <span
            class="mt-1.5 size-2 shrink-0 rounded-full"
            :class="item.readAt ? 'bg-transparent' : 'bg-primary'"
            aria-hidden="true"
          />
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-ink">{{ item.title }}</p>
            <p v-if="item.body" class="mt-1 text-xs leading-relaxed text-muted">{{ item.body }}</p>
            <p class="mt-1 text-xs text-muted" :title="formatAbsoluteTime(item.createdAt)">
              {{ formatRelativeTime(item.createdAt) }}
            </p>
          </div>
        </component>
      </li>
    </ul>

    <div v-if="!loading && items.length && hasMore" class="mt-4 flex justify-center">
      <BaseButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMore">
        加载更多
      </BaseButton>
    </div>
  </div>
</template>
