<script setup lang="ts">
import { ref } from 'vue'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import { useUserLabels } from '@shared/composables/useUserLabels'

/**
 * 下架帖的作者申诉面板（详情页里作者唯一的申诉入口）。
 *
 * 只在下架态展示；每帖限一次、限下架后 7 日内，越界由后端回 409 并由拦截器提示。
 * 申诉理由状态收在本组件内，成功后父级把 `appealed` 置真，面板即切换为「已提交」文案
 * （无需再清空输入）。
 */
const props = withDefaults(
  defineProps<{
    /** 下架处置原因（后端原文，可能为 null） */
    reviewReason: string | null
    /** 是否已提交过申诉 */
    appealed: boolean
    submitting?: boolean
  }>(),
  { submitting: false },
)

const emit = defineEmits<{ submit: [reason: string] }>()

const { reviewReasonText } = useUserLabels()

const reason = ref('')

function handleSubmit(): void {
  const text = reason.value.trim()
  if (!text) {
    return
  }
  emit('submit', text)
}
</script>

<template>
  <div class="mt-3 rounded-lg bg-danger-soft px-3 py-2.5">
    <p class="text-xs font-medium text-danger-deep">内容已下架，他人不可见</p>
    <p v-if="reviewReasonText(props.reviewReason)" class="mt-1 text-xs text-danger-deep">
      处置原因：{{ reviewReasonText(props.reviewReason) }}
    </p>

    <p v-if="props.appealed" class="mt-2 text-xs text-danger-deep">
      申诉已提交，将由另一位管理员复审
    </p>
    <template v-else>
      <BaseTextarea
        v-model="reason"
        name="appealReason"
        aria-label="申诉理由"
        class="mt-2"
        :rows="3"
        :maxlength="500"
        placeholder="说明申诉理由，最多 500 字"
      />
      <div class="mt-2 flex justify-end">
        <BaseButton
          size="sm"
          :loading="props.submitting"
          :disabled="!reason.trim()"
          @click="handleSubmit"
        >
          提交申诉
        </BaseButton>
      </div>
    </template>
  </div>
</template>
