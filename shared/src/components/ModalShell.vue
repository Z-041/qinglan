<script setup lang="ts">
import { ref } from 'vue'

import { useFocusTrap } from '@shared/composables/useFocusTrap'

/**
 * 自绘弹层的统一外壳：全屏遮罩 + 居中面板 + ESC / 遮罩关闭 + 焦点陷阱。双端共用
 * （学生端 ReportDialog / ConfirmDialog、管理端各处置弹窗）。
 *
 * 学生端的举报弹窗与删除评论确认此前各写一份同样的遮罩/ESC/焦点代码（AGENTS.md 要求超过 3 处
 * 重复即抽）；公告弹窗不走这里——它是强读单例，遮罩 / ESC / 返回键都不关闭，语义不同。
 *
 * z 轴分层：页头 z-header < 本弹层 z-overlay < 公告 z-announcement < 全局提示 z-toast
 * （完整口径见 shared/uno.base.ts）。窄屏贴底（max-sm:items-end）是学生端的既有习惯，
 * 管理端仅支持 PC，不受影响。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    /** 可访问名（role=dialog 的 aria-label） */
    label: string
    /**
     * 遮罩点击与 ESC 是否都能关闭。两者在真实调用点**永远同值**（都是「提交在途则不可关」），
     * 故合成一个开关，避免出现「能点遮罩关、ESC 不能关」这类自相矛盾。
     */
    dismissible?: boolean
    /** 面板宽度类（默认 max-w-md）；窄确认框传 max-w-sm */
    panelClass?: string
    /** 面板附加类（如管理端的固定宽度 / 高度上限） */
    panelExtraClass?: string
  }>(),
  { dismissible: true, panelClass: 'max-w-md', panelExtraClass: '' },
)

const emit = defineEmits<{ close: [] }>()

const dialogRef = ref<HTMLElement | null>(null)

// 打开时收焦点、Tab 在弹层内循环、关闭时把焦点还给打开前的元素
useFocusTrap(dialogRef, () => props.open)
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
    @click.self="props.dismissible && emit('close')"
    @keydown.escape="props.dismissible && emit('close')"
  >
    <div
      class="w-full rounded-xl border border-line bg-surface p-4 shadow-xl"
      :class="[props.panelClass, props.panelExtraClass]"
    >
      <slot />
    </div>
  </div>
</template>
