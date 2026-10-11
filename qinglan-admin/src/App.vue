<script setup lang="ts">
import ToastHost from '@shared/components/ToastHost.vue'
import MessageBoxHost from '@/components/base/MessageBoxHost.vue'

/**
 * 管理端根组件。
 *
 * 命令式确认 / 输入弹窗（替代 ElMessageBox）由常驻的 MessageBoxHost 渲染，用 ModalShell 做外壳，
 * 与全站其它弹层的遮罩 / ESC / 焦点口径一致。
 *
 * 管理端不挂 `AnnouncementModal`、不渲染学生端头部/页脚——拆分后这些分支判断已无必要。
 */
</script>

<template>
  <RouterView />
  <MessageBoxHost />
  <ToastHost />
</template>
