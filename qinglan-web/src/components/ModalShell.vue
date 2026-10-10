<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

/**
 * 学生端自绘弹层的统一外壳：全屏遮罩 + 居中面板 + ESC / 遮罩关闭 + 打开时收焦点。
 *
 * 举报弹窗与删除评论确认此前各写一份同样的遮罩/ESC/焦点代码（AGENTS.md 要求超过 3 处
 * 重复即抽；这是第 2、3 处，提前收口避免继续漂移）。公告弹窗不在此列——它是强读单例，
 * 自带焦点陷阱与返回键守卫，语义不同。
 *
 * z 轴分层：页头 z-header < 本弹层 z-overlay < 公告 z-announcement < 全局提示 z-toast
 * （完整口径见 shared/uno.config.ts）。窄屏贴底（max-sm:items-end）是学生端的既有习惯。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 可访问名（role=dialog 的 aria-label） */
    label: string
    /** 遮罩点击是否关闭；提交在途等场景传 false */
    closeOnBackdrop?: boolean
    /** ESC 是否关闭；与 closeOnBackdrop 同口径传入即可 */
    closeOnEsc?: boolean
    /** 面板宽度类（默认 max-w-md）；窄确认框传 max-w-sm */
    panelClass?: string
  }>(),
  { closeOnBackdrop: true, closeOnEsc: true, panelClass: 'max-w-md' },
)

const emit = defineEmits<{ close: [] }>()

const dialogRef = ref<HTMLElement | null>(null)

// 打开时把焦点收进弹窗：容器带 tabindex="-1"，获得焦点后 ESC 才生效，
// 键盘用户也不会把焦点留在背后的页面上
watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return
    }
    await nextTick()
    dialogRef.value?.focus()
  },
)
</script>

<template>
  <div
    v-if="props.open"
    ref="dialogRef"
    tabindex="-1"
    class="fixed inset-0 z-overlay flex overscroll-contain items-center justify-center bg-black/40 p-4 outline-none max-sm:items-end"
    role="dialog"
    aria-modal="true"
    :aria-label="props.label"
    @click.self="props.closeOnBackdrop && emit('close')"
    @keydown.escape="props.closeOnEsc && emit('close')"
  >
    <div class="w-full rounded-xl border border-line bg-surface p-4 shadow-xl" :class="props.panelClass">
      <slot />
    </div>
  </div>
</template>
