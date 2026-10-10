<script setup lang="ts">
import { ref } from 'vue'

import RelativeTime from '@shared/components/RelativeTime.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'
import { auditActionLabel } from '@/types/admin'
import type { AdminReviewContext, AdminReviewHistory } from '@/types/review'

const props = defineProps<{
  open: boolean
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

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { targetTypeLabel, targetStatusLabel, reportReasonLabel } = useUserLabels()

/** 当前 Tab；默认「举报概况」 */
const activeTab = ref('context')
</script>

<template>
  <el-drawer
    :model-value="props.open"
    title="审核上下文"
    size="480px"
    @update:model-value="emit('update:open', $event)"
  >
    <p v-if="props.loading && !props.context" class="text-sm text-muted">加载中…</p>

    <p v-else-if="props.failed && !props.context" class="text-sm text-muted">
      上下文没能加载出来，请关闭面板后重试
    </p>

    <div v-else-if="props.context" class="flex flex-col gap-5">
      <!-- 三个 Tab 都只展示聚合/流水信息；处置按钮组在 Tab 之外，切到历史也随时可操作 -->
      <el-tabs v-model="activeTab">
        <el-tab-pane label="举报概况" name="context">
          <dl class="flex flex-col gap-3 text-sm">
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
        </el-tab-pane>

        <el-tab-pane label="作者动态" name="author">
          <p v-if="props.historyLoading && !props.history" class="text-sm text-muted">加载中…</p>
          <p v-else-if="props.historyFailed && !props.history" class="text-sm text-muted">
            作者动态没能加载出来
          </p>
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
          <p v-else class="text-sm text-muted">暂无近期动态</p>
        </el-tab-pane>

        <el-tab-pane label="处置历史" name="history">
          <p v-if="props.historyLoading && !props.history" class="text-sm text-muted">加载中…</p>
          <p v-else-if="props.historyFailed && !props.history" class="text-sm text-muted">
            处置历史没能加载出来
          </p>
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
          <p v-else class="text-sm text-muted">暂无处置记录</p>
        </el-tab-pane>
      </el-tabs>

      <!-- 处置按钮组由调用方插槽注入，面板只负责展示上下文 -->
      <slot />
    </div>
  </el-drawer>
</template>
