<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import FormField from '@shared/components/base/FormField.vue'
import DialogShell from './DialogShell.vue'

const props = defineProps<{ open: boolean; submitting?: boolean; initialApproved?: boolean }>()

const emit = defineEmits<{ submit: [approved: boolean, reason: string]; close: [] }>()

/** true = 通过申诉（内容恢复可见），false = 维持原判；默认值与既有行为一致 */
const APPROVED_DEFAULT = true

/** true = 通过申诉（内容恢复可见），false = 维持原判 */
const approved = ref(APPROVED_DEFAULT)
const reason = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      approved.value = props.initialApproved ?? APPROVED_DEFAULT
      reason.value = ''
    }
  },
)

const canSubmit = computed(() => reason.value.trim().length > 0)

function handleSubmit(): void {
  if (!canSubmit.value) {
    return
  }
  emit('submit', approved.value, reason.value.trim())
}
</script>

<template>
  <DialogShell
    :open="props.open"
    title="申诉复审"
    confirm-text="提交复审"
    :submitting="props.submitting"
    :confirm-disabled="!canSubmit"
    @close="emit('close')"
    @confirm="handleSubmit"
  >
    <div class="flex flex-col gap-4">
      <!-- 单选组**不能**用 FormField 包：FormField 的根是 <label>（它是为单个输入框设计的），
           再套一层 radio 的 <label> 就构成非法嵌套；而 HTML 规定无 for 的 <label> 会关联
           树序上第一个 labelable 后代——也就是「通过申诉」。结果点一下分组标题「复审结论」
           就会把结论静默改成「通过申诉」（内容恢复可见，属不可逆处置）。
           ReportDialog 的「举报理由」用的是同一套 fieldset/legend 写法。 -->
      <fieldset>
        <legend class="ql-label">复审结论</legend>
        <div class="flex gap-2">
          <label class="cursor-pointer">
            <input v-model="approved" class="peer sr-only" type="radio" name="appeal-verdict" :value="true" />
            <span
              class="block rounded-lg border border-line px-3 py-2 text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep) peer-focus-visible:ring-2 peer-focus-visible:ring-primary peer-focus-visible:ring-offset-1"
            >
              通过申诉（恢复内容）
            </span>
          </label>
          <label class="cursor-pointer">
            <input v-model="approved" class="peer sr-only" type="radio" name="appeal-verdict" :value="false" />
            <span
              class="block rounded-lg border border-line px-3 py-2 text-xs text-muted transition peer-checked:(border-primary bg-primary-soft font-medium text-primary-deep) peer-focus-visible:ring-2 peer-focus-visible:ring-primary peer-focus-visible:ring-offset-1"
            >
              维持原判
            </span>
          </label>
        </div>
      </fieldset>

      <FormField label="复审说明" hint="会写入审计日志">
        <el-input v-model="reason" type="textarea" :rows="3" :maxlength="500" placeholder="说明复审依据" />
      </FormField>
    </div>
  </DialogShell>
</template>
