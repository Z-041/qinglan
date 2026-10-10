<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import BaseButton from '@shared/components/base/BaseButton.vue'
import BaseCard from '@shared/components/base/BaseCard.vue'
import BaseInput from '@shared/components/base/BaseInput.vue'
import BaseTextarea from '@shared/components/base/BaseTextarea.vue'
import FormField from '@shared/components/base/FormField.vue'
import ImagePicker from '@/components/ImagePicker.vue'
import SidebarCard from '@/components/SidebarCard.vue'
import TwoColumnLayout from '@/components/TwoColumnLayout.vue'
import { MAX_IMAGES_PER_POST, useImageUpload } from '@/composables/useImageUpload'
import { usePostDraft } from '@/composables/usePostDraft'
import { usePublish } from '@/composables/usePublish'
import { pushToast } from '@shared/composables/useToast'

const router = useRouter()

const { uploading: uploadingImages, addFiles, refresh: refreshDraftImages } = useImageUpload()

const {
  topics,
  topicId,
  content,
  contact,
  images,
  submitting,
  topicsFailed,
  selectedTopic,
  blockedReason,
  contactRequired,
  canSubmit,
  loadTopics,
  submit,
} = usePublish()

// 草稿与表单字段绑定：变更即写入 localStorage，意外退出可恢复
const {
  pending: pendingDraft,
  restore: restoreDraft,
  discard: discardDraft,
  clear: clearDraft,
} = usePostDraft({ topicId, content, contact, images })

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

/**
 * 恢复草稿：先回填字段，再用批量端点把草稿里的原 URL 换成当前可用的地址。
 *
 * 私有图在浏览器 `<img>` 请求里不带令牌，草稿保存的原 URL 取不到图；换取不到的（已清理 / 无权访问）
 * 从列表中剔除并明确提示「需重新上传」，避免留下破图。换取请求失败由拦截器统一提示，此处保留原值。
 */
async function handleRestoreDraft(): Promise<void> {
  restoreDraft()
  const restored = images.value
  if (!restored.length) {
    return
  }
  try {
    const { images: usable, missing } = await refreshDraftImages(restored)
    images.value = usable
    if (missing > 0) {
      pushToast(`${missing} 张图片已失效，请重新上传`, 'error')
    }
  } catch {
    // 换取失败：错误已由拦截器提示，保留原地址（公开图仍可正常显示）
  }
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
  // 发布成功即清空草稿：否则下次进入会提示「恢复」一条已经发出去的帖子
  clearDraft()
  pushToast('已提交，审核通过后会出现在首页', 'info')
  await router.push('/')
}
</script>

<template>
  <section class="ql-shell py-8 max-lg:py-5">
    <h1 class="text-lg font-semibold text-ink">发布</h1>
    <p class="mt-1 text-xs text-muted">主帖先审后发，通过后才会出现在首页</p>

    <!-- 草稿恢复：显式「恢复 / 丢弃」，不静默覆盖用户新输入 -->
    <div
      v-if="pendingDraft"
      class="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-warning bg-warning-soft px-4 py-3"
    >
      <p class="min-w-0 flex-1 text-xs leading-relaxed text-warning">
        发现上次未提交的草稿（含正文 / 图片），是否恢复？恢复会覆盖当前输入。
      </p>
      <BaseButton size="sm" @click="handleRestoreDraft">恢复草稿</BaseButton>
      <BaseButton variant="ghost" size="sm" @click="discardDraft">丢弃</BaseButton>
    </div>

    <TwoColumnLayout class="mt-4">
      <BaseCard>
          <div>
            <span class="ql-label">选择话题</span>
            <!-- 卡片式单选：每张卡展示话题规则与是否需要联系方式 -->
            <div v-if="topics.length" class="grid grid-cols-2 gap-2 max-sm:grid-cols-1">
              <button
                v-for="topic in topics"
                :key="topic.id"
                type="button"
                class="cursor-pointer rounded-lg border p-3 text-left transition"
                :class="
                  topicId === topic.id
                    ? 'border-primary-deep bg-primary-tint'
                    : 'border-line hover:border-primary-soft'
                "
                :aria-pressed="topicId === topic.id"
                @click="topicId = topic.id"
              >
                <span class="flex items-center justify-between gap-2">
                  <span
                    class="text-sm font-medium"
                    :class="topicId === topic.id ? 'text-primary-deep' : 'text-ink'"
                  >
                    {{ topic.name }}
                  </span>
                  <span
                    v-if="topic.requiresContact"
                    class="ql-tag shrink-0 bg-warning-soft text-warning"
                  >
                    需联系方式
                  </span>
                </span>
                <span class="mt-1 block text-xs leading-relaxed text-muted">{{ topic.rules }}</span>
              </button>
            </div>
            <p v-else class="text-xs text-muted">暂无可用话题</p>
            <!-- 话题是发帖的前置：拉取失败时给出「重试」，否则话题区永远空白、
                 发布按钮永远灰着却不说为什么（具体错误信息仍由拦截器 toast 给出） -->
            <p v-if="topicsFailed" class="mt-2 text-xs text-muted">
              话题没能加载出来
              <BaseButton class="ml-1" variant="ghost" size="sm" @click="loadTopics">重试</BaseButton>
            </p>
          </div>

          <div class="mt-5">
            <!-- 窄屏右栏隐藏，须知在表单内回显；PC 由右栏「发布须知」卡承担 -->
            <p
              class="mb-2 hidden rounded-lg bg-warning-soft px-3 py-2 text-xs leading-relaxed text-warning max-lg:block"
            >
              {{ PUBLISH_NOTICE }}
            </p>
            <FormField label="正文" :hint="`${content.length}/2000`">
              <BaseTextarea v-model="content" :rows="8" :maxlength="2000" placeholder="说点什么…" />
            </FormField>
            <p v-if="blockedReason" class="ql-error" role="alert">{{ blockedReason }}</p>
          </div>

          <div class="mt-5" role="group" :aria-label="`图片（最多 ${MAX_IMAGES_PER_POST} 张）`">
            <span class="ql-label">图片（最多 {{ MAX_IMAGES_PER_POST }} 张）</span>
            <!-- 显式传 max：上限只认 useImageUpload 的常量，不让组件默认值与它各写一份 -->
            <ImagePicker
              v-model="images"
              :max="MAX_IMAGES_PER_POST"
              :uploading="uploadingImages"
              @select="handleSelectImages"
            />
          </div>

          <!-- 联系方式按话题要求显隐：未选话题时不展示字段，选定后按是否必填给出对应标签 -->
          <div class="mt-5">
            <FormField
              v-if="topicId !== null"
              :label="contactLabel"
              hint="不会展示给游客，仅认证学生可见；被人查看会留下审计记录"
            >
              <BaseInput v-model="contact" :maxlength="200" placeholder="微信号 / QQ / 手机号" />
            </FormField>
            <p v-else class="ql-hint">选择话题后可填写联系方式，部分话题要求必填。</p>
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

      <template #aside>
        <SidebarCard title="话题规则">
          <p class="mt-2 text-xs leading-relaxed text-muted">
            {{ selectedTopic ? selectedTopic.rules : '选择一个话题后显示该话题的规则' }}
          </p>
        </SidebarCard>

        <SidebarCard title="发布须知">
          <p class="mt-2 text-xs leading-relaxed text-muted">{{ PUBLISH_NOTICE }}</p>
        </SidebarCard>
      </template>
    </TwoColumnLayout>
  </section>
</template>
