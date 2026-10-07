<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{ max?: number; uploading?: boolean }>(), {
  max: 9,
  uploading: false,
})

/** 已上传的图片地址；上传动作由父级 composable 负责，本组件只负责选择与展示 */
const model = defineModel<string[]>({ default: () => [] })

const emit = defineEmits<{ select: [files: File[]] }>()

const fileInput = ref<HTMLInputElement | null>(null)

/** 只把选中的文件交出去，上传与配额由调用方（useImageUpload）负责 */
function handleFiles(event: Event): void {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files ?? [])
  // 清空 value，否则再次选择同一文件不会触发 change
  target.value = ''
  if (files.length) {
    emit('select', files)
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
      :disabled="props.uploading"
      @click="fileInput?.click()"
    >
      <span
        v-if="props.uploading"
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
      <span>{{ props.uploading ? '上传中' : '添加图片' }}</span>
    </button>

    <!-- accept 与后端 ImageType（JPEG / PNG / WEBP）白名单对齐：宽泛的 image/* 会让用户选中
         GIF 等后端拒收的类型，白跑一次上传才收到错误提示 -->
    <input
      ref="fileInput"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      multiple
      class="hidden"
      @change="handleFiles"
    />
  </div>
</template>
