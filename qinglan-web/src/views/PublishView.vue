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
import { MAX_IMAGES_PER_POST, useImageUpload } from '@/composables/useImageUpload'
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

/** 发布须知文案：PC 由右栏卡承载，窄屏右栏隐藏时在表单内回显，避免任一宽度丢信息 */
const PUBLISH_NOTICE =
  '禁止发布：姓名、学号、宿舍号、联系方式。联系方式请填写到下方独立字段，只对认证用户可见。'

onMounted(() => {
  void loadTopics()
})

/**
 * 只把**本次新增**的地址按「当时的**列表**」合并进来。
 * 不能在 await 前读快照再整份赋值：上传期间用户删掉的图会复活、连选两次会丢掉前一次的结果。
 * 上限也在此刻按当时的列表再兜一次（上传途中删图会让 useImageUpload 里算的 remaining 偏大）。
 */
async function handleSelectImages(files: File[]): Promise<void> {
  const added = await addFiles(files, images.value)
  if (!added.length) {
    return
  }
  const merged = [...images.value, ...added.filter((url) => !images.value.includes(url))]
  images.value = merged.slice(0, MAX_IMAGES_PER_POST)
}

async function handleSubmit(): Promise<void> {
  // 图片还在上传时不允许提交：此时 images 里没有这些图，提交会发出缺图的帖子，
  // 上传完成后页面已卸载，图被静默丢弃（对象存储里留孤儿）
  if (uploadingImages.value) {
    return
  }
  const created = await submit()
  if (!created) {
    return
  }
  pushToast('已提交，审核通过后会出现在首页', 'info')
  await router.push('/')
}
</script>

<template>
  <section class="ql-shell py-8 max-lg:py-5">
    <h1 class="text-lg font-semibold text-ink">发布</h1>
    <p class="mt-1 text-xs text-muted">主帖先审后发，通过后才会出现在首页</p>

    <div class="mt-4 grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(grid-cols-1 gap-6)">
      <div class="min-w-0 self-start">
        <BaseCard>
          <div>
            <span class="ql-label">选择话题</span>
            <TopicFilter v-model="topicId" :topics="topics" :allow-all="false" />
            <!-- 窄屏右栏隐藏，话题规则在表单内回显；PC 由右栏「话题规则」卡承担，避免同一屏重复 -->
            <div v-if="selectedTopic" class="hidden max-lg:block">
              <p class="ql-hint">话题规则：{{ selectedTopic.rules }}</p>
            </div>
          </div>

          <div class="mt-5">
            <!-- 窄屏右栏隐藏，须知在表单内回显；PC 由右栏「发布须知」卡承担 -->
            <p
              class="mb-2 hidden rounded-lg bg-warning-soft px-3 py-2 text-xs leading-relaxed text-warning max-lg:block"
            >
              {{ PUBLISH_NOTICE }}
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
            <ImagePicker v-model="images" :uploading="uploadingImages" @select="handleSelectImages" />
          </div>

          <BaseButton
            class="mt-6"
            block
            :loading="submitting"
            :disabled="!canSubmit || uploadingImages"
            @click="handleSubmit"
          >
            发布
          </BaseButton>
        </BaseCard>
      </div>

      <aside class="sticky top-24 flex flex-col gap-4 self-start max-lg:hidden">
        <div class="ql-card p-4">
          <p class="text-sm font-semibold text-ink">话题规则</p>
          <p class="mt-2 text-xs leading-relaxed text-muted">
            {{ selectedTopic ? selectedTopic.rules : '选择一个话题后显示该话题的规则' }}
          </p>
        </div>

        <div class="ql-card p-4">
          <p class="text-sm font-semibold text-ink">发布须知</p>
          <p class="mt-2 text-xs leading-relaxed text-muted">{{ PUBLISH_NOTICE }}</p>
        </div>
      </aside>
    </div>
  </section>
</template>
