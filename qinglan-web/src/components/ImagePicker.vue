<script setup lang="ts">
import { ref } from 'vue'

import { uploadImage } from '@/api/upload'

const props = withDefaults(defineProps<{ max?: number }>(), { max: 9 })

const model = defineModel<string[]>({ default: () => [] })

const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

/**
 * 逐个上传并在成功后立即追加，中途失败时已传成功的仍保留。
 * 失败提示由 http 拦截器统一弹窗（AGENTS.md 第七章），这里只负责结束上传态。
 */
async function handleFiles(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  // 清空 value，否则再次选择同一文件不会触发 change
  target.value = ''
  const remaining = props.max - model.value.length
  if (!files.length || remaining <= 0) {
    return
  }

  uploading.value = true
  try {
    for (const file of files.slice(0, remaining)) {
      const { url } = await uploadImage(file)
      model.value = [...model.value, url]
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    uploading.value = false
  }
}

function remove(url: string): void {
  model.value = model.value.filter((item) => item !== url)
}
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <div v-for="url in model" :key="url" class="relative">
      <img :src="url" alt="已上传图片" class="size-20 rounded-lg border border-line object-cover" />
      <button
        type="button"
        class="absolute -top-1.5 -right-1.5 flex size-5 cursor-pointer items-center justify-center rounded-full bg-danger text-xs leading-none text-white"
        aria-label="移除图片"
        @click="remove(url)"
      >
        ×
      </button>
    </div>

    <button
      v-if="model.length < props.max"
      type="button"
      class="flex size-20 cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-line-strong text-xs text-muted transition hover:border-primary hover:text-primary-deep disabled:cursor-not-allowed"
      :disabled="uploading"
      @click="fileInput?.click()"
    >
      <span
        v-if="uploading"
        class="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        aria-hidden="true"
      />
      <svg
        v-else
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        class="size-5"
        aria-hidden="true"
      >
        <path d="M12 6v12M6 12h12" stroke-linecap="round" />
      </svg>
      <span>{{ uploading ? '上传中' : '添加图片' }}</span>
    </button>

    <input ref="fileInput" type="file" accept="image/*" multiple class="hidden" @change="handleFiles" />
  </div>
</template>
