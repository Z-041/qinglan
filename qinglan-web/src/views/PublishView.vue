<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import BaseButton from '@/components/base/BaseButton.vue'
import BaseCard from '@/components/base/BaseCard.vue'
import BaseInput from '@/components/base/BaseInput.vue'
import BaseTextarea from '@/components/base/BaseTextarea.vue'
import FormField from '@/components/base/FormField.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import TopicFilter from '@/components/TopicFilter.vue'
import { useImageUpload } from '@/composables/useImageUpload'
import { usePublish } from '@/composables/usePublish'
import { pushToast } from '@/composables/useToast'

const router = useRouter()

const { uploading: uploadingImages, addFiles } = useImageUpload()

const {
  topics,
  topicId,
  content,
  contact,
  images,
  submitting,
  selectedTopic,
  blockedReason,
  contactRequired,
  canSubmit,
  loadTopics,
  submit,
} = usePublish()

const contactLabel = computed(() =>
  contactRequired.value ? '联系方式（该话题必填）' : '联系方式（选填）',
)

onMounted(() => {
  void loadTopics()
})

async function handleSelectImages(files: File[]): Promise<void> {
  images.value = await addFiles(files, images.value)
}

async function handleSubmit(): Promise<void> {
  const created = await submit()
  if (!created) {
    return
  }
  pushToast('已提交，审核通过后会出现在首页', 'info')
  await router.push('/')
}
</script>

<template>
  <section class="py-2">
    <h1 class="text-lg font-semibold text-ink">发布</h1>
    <p class="mt-1 text-xs text-muted">主帖先审后发，通过后才会出现在首页</p>

    <BaseCard class="mt-4">
      <div>
        <span class="ql-label">选择话题</span>
        <TopicFilter v-model="topicId" :topics="topics" :allow-all="false" />
        <p v-if="selectedTopic" class="ql-hint">话题规则：{{ selectedTopic.rules }}</p>
      </div>

      <div class="mt-5">
        <p class="mb-2 rounded-lg bg-warning-soft px-3 py-2 text-xs leading-relaxed text-warning">
          禁止发布：姓名、学号、宿舍号、联系方式。联系方式请填写到下方独立字段，只对认证用户可见。
        </p>
        <FormField label="正文" :hint="`${content.length}/2000`">
          <BaseTextarea v-model="content" :rows="6" :maxlength="2000" placeholder="说点什么…" />
        </FormField>
        <p v-if="blockedReason" class="ql-error" role="alert">{{ blockedReason }}</p>
      </div>

      <div class="mt-5">
        <FormField
          :label="contactLabel"
          hint="不会展示给游客，仅认证学生可见；被人查看会留下审计记录"
        >
          <BaseInput v-model="contact" :maxlength="200" placeholder="微信号 / QQ / 手机号" />
        </FormField>
      </div>

      <div class="mt-5">
        <span class="ql-label">图片（最多 9 张）</span>
        <ImagePicker
          v-model="images"
          :uploading="uploadingImages"
          @select="handleSelectImages"
        />
      </div>

      <BaseButton class="mt-6" block :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
        发布
      </BaseButton>
    </BaseCard>
  </section>
</template>
