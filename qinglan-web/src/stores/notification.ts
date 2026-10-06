import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 未读通知计数。
 *
 * 按 AGENTS.md 第七章，store 只放用户态 / 未读计数 / 草稿，不承载业务规则：
 * 何时拉取、何时清零由 `useNotifications` 编排列决定，这里只提供读写入口。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)

  function setUnreadCount(value: number): void {
    unreadCount.value = value
  }

  /** 退出登录时清零，避免下个身份的旧红点残留 */
  function reset(): void {
    unreadCount.value = 0
  }

  return { unreadCount, setUnreadCount, reset }
})
