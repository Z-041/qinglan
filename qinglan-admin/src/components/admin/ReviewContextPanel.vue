<script setup lang="ts">
import { ref } from 'vue'

import RelativeTime from '@shared/components/RelativeTime.vue'
import InlineState from '@/components/admin/InlineState.vue'
import BaseDrawer from '@/components/base/BaseDrawer.vue'
import BaseTabs from '@/components/base/BaseTabs.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { auditActionLabel } from '@/types/admin'
import type { AdminReviewContext, AdminReviewHistory } from '@/types/review'

const props = defineProps<{
  context: AdminReviewContext | null
  /** 作者最近动态 + 目标处置历史（只含 ID / 状态 / 动作，不含学号邮箱） */
  history?: AdminReviewHistory | null
  loading?: boolean
  historyLoading?: boolean
  /** 概况请求失败：给出说明而不是留一片空白抽屉 */
  failed?: boolean
  /** 历史请求失败 */
  historyFailed?: boolean
}>()

/** 由调用方 v-model:open 控制开关（与原 el-drawer 口径一致） */
const open = defineModel<boolean>('open', { required: true })

const { targetTypeLabel, targetStatusLabel, reportReasonLabel } = useUserLabels()

/** 当前 Tab；默认「举报概况」 */
const activeTab = ref('context')
const TABS = [
  { name: 'context', label: '举报概况' },
  { name: 'author', label: '作者动态' },
  { name: 'history', label: '处置历史' },
]
</script>

<template>
  <BaseDrawer :open="open" title="审核上下文" @close="open = false">
    <InlineState v-if="props.loading && !props.context" loading />

    <InlineState v-else-if="props.failed && !props.context">
      上下文没能加载出来，请关闭面板后重试
    </InlineState>

    <div v-else-if="props.context" class="flex flex-col gap-5">
      <!-- 三个 Tab 都只展示聚合/流水信息；处置按钮组在 Tab 之外，切到历史也随时可操作 -->
      <BaseTabs v-model="activeTab" :tabs="TABS" aria-label="审核上下文" />

      <dl v-show="activeTab === 'context'" class="flex flex-col gap-3 text-sm">
        <div class="flex justify-between gap-4">
          <dt class="text-muted">目标类型</dt>
          <dd class="text-ink">{{ targetTypeLabel(props.context.targetType) }}</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">目标状态</dt>
          <dd class="text-ink">{{ targetStatusLabel(props.context.targetStatus) }}</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">作者信用分</dt>
          <dd class="text-ink">{{ props.context.authorCreditScore }}</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">作者累计被处置</dt>
          <dd class="text-ink">{{ props.context.authorViolationCount }} 次</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">待处理举报</dt>
          <dd class="text-ink">{{ props.context.reportCount }} 条</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">举报人数（去重）</dt>
          <dd class="text-ink">{{ props.context.distinctReporterCount }} 人</dd>
        </div>
        <div class="flex justify-between gap-4">
          <dt class="text-muted">其中重复举报人</dt>
          <dd class="text-ink">{{ props.context.repeatedReporterCount }} 人</dd>
        </div>
        <div>
          <dt class="text-muted">举报理由</dt>
          <dd class="mt-1.5 flex flex-wrap gap-1.5">
            <span
              v-for="reason in props.context.reasons"
              :key="reason"
              class="ql-badge bg-primary-tint text-primary-deep"
            >
              {{ reportReasonLabel(reason) }}
            </span>
          </dd>
        </div>
      </dl>

      <div v-show="activeTab === 'author'">
        <InlineState v-if="props.historyLoading && !props.history" loading />
        <InlineState v-else-if="props.historyFailed && !props.history">
          作者动态没能加载出来
        </InlineState>
        <ul v-else-if="props.history?.authorRecentPosts.length" class="flex flex-col gap-3 text-sm">
          <li
            v-for="post in props.history.authorRecentPosts"
            :key="post.id"
            class="flex items-center justify-between gap-4"
          >
            <!-- 只展示 ID / 状态 / 时间，绝不展示学号邮箱或匿名代号 -->
            <span class="text-ink">帖子 #{{ post.id }}</span>
            <span class="text-muted">{{ targetStatusLabel(post.status) }}</span>
            <RelativeTime class="text-muted" :iso="post.createdAt" />
          </li>
        </ul>
        <InlineState v-else>暂无近期动态</InlineState>
      </div>

      <div v-show="activeTab === 'history'">
        <InlineState v-if="props.historyLoading && !props.history" loading />
        <InlineState v-else-if="props.historyFailed && !props.history">
          处置历史没能加载出来
        </InlineState>
        <ul v-else-if="props.history?.targetDispositions.length" class="flex flex-col gap-3 text-sm">
          <li
            v-for="item in props.history.targetDispositions"
            :key="`${item.action}-${item.operatorId}-${item.createdAt}`"
            class="flex flex-col gap-1"
          >
            <div class="flex items-center justify-between gap-4">
              <span class="text-ink">{{ auditActionLabel(item.action) }}</span>
              <RelativeTime class="text-muted" :iso="item.createdAt" />
            </div>
            <p class="text-xs text-muted">
              <!-- 0 是系统自动处置的哨兵值；操作人只以 ID 出现 -->
              操作人：{{ item.operatorId === 0 ? '系统' : `#${item.operatorId}` }}
              <span v-if="item.reason"> · {{ item.reason }}</span>
            </p>
          </li>
        </ul>
        <InlineState v-else>暂无处置记录</InlineState>
      </div>

      <!-- 处置按钮组由调用方插槽注入，面板只负责展示上下文 -->
      <slot />
    </div>
  </BaseDrawer>
</template>
