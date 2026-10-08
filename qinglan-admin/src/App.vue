<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import ToastHost from '@shared/components/ToastHost.vue'

/**
 * 管理端根组件。
 *
 * `el-config-provider` 放在这里而不是 `AdminLayout` 里：`/admin/login` 是与 AdminLayout
 * **同级**的路由、不经过它，放在 layout 里登录页就拿不到中文语言包（原单应用形态下的遗留问题）。
 *
 * 管理端不挂 `AnnouncementModal`、不渲染学生端头部/页脚——拆分后这些分支判断已无必要。
 */
</script>

<template>
  <el-config-provider :locale="zhCn">
    <RouterView />
    <ToastHost />
  </el-config-provider>
</template>
