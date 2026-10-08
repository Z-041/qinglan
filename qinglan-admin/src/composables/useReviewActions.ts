import { ref } from 'vue'

import {
  acceptAppeal,
  approvePost,
  dismissReports,
  hidePostContact,
  rejectAppeal,
  rejectPost,
  removeComment,
  restorePost,
  showPostContact,
  takeDownPost,
  upholdReports,
} from '@/api/review'
import type { DismissReportsPayload, ReasonPayload, TakeDownPayload } from '@/types/review'

/**
 * 处置动作编排：把「提交中」状态与「成功 / 失败」语义收在一处，
 * 视图只关心成功后做什么（刷新列表、关闭面板）。
 * 失败一律由 http 拦截器提示，这里不重复渲染文案。
 */
export function useReviewActions() {
  const submitting = ref(false)

  async function run(action: () => Promise<void>): Promise<boolean> {
    submitting.value = true
    try {
      await action()
      return true
    } catch {
      // 错误已由拦截器提示
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    submitting,
    approvePost: (postId: number) => run(() => approvePost(postId)),
    rejectPost: (postId: number, payload: ReasonPayload) => run(() => rejectPost(postId, payload)),
    takeDownPost: (postId: number, payload: TakeDownPayload) => run(() => takeDownPost(postId, payload)),
    restorePost: (postId: number, payload: ReasonPayload) => run(() => restorePost(postId, payload)),
    hidePostContact: (postId: number, payload: ReasonPayload) => run(() => hidePostContact(postId, payload)),
    showPostContact: (postId: number, payload: ReasonPayload) => run(() => showPostContact(postId, payload)),
    removeComment: (commentId: number, payload: ReasonPayload) => run(() => removeComment(commentId, payload)),
    dismissReports: (payload: DismissReportsPayload) => run(() => dismissReports(payload)),
    upholdReports: (postId: number, payload: ReasonPayload) => run(() => upholdReports(postId, payload)),
    acceptAppeal: (appealId: number, payload: ReasonPayload) => run(() => acceptAppeal(appealId, payload)),
    rejectAppeal: (appealId: number, payload: ReasonPayload) => run(() => rejectAppeal(appealId, payload)),
  }
}
