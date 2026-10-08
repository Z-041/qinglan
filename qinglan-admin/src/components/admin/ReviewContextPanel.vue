<script setup lang="ts">
import { useUserLabels } from '@shared/composables/useUserLabels'
import type { AdminReviewContext } from '@/types/review'

const props = defineProps<{
  open: boolean
  context: AdminReviewContext | null
  loading?: boolean
  /** 上下文请求失败：给出说明而不是留一片空白抽屉 */
  failed?: boolean
}>()

const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const { targetTypeLabel, targetStatusLabel, reportReasonLabel } = useUserLabels()
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

      <!-- 处置按钮组由调用方插槽注入，面板只负责展示上下文 -->
      <slot />
    </div>
  </el-drawer>
</template>
