import { useSubmit } from '@shared/composables/useSubmit'

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
import type { DismissReportsPayload, ProposalDispositionPayload, ReasonPayload } from '@/types/review'

/**
 * 处置动作编排：提交态与「成功 / 失败」语义交给 {@link useSubmit}，
 * 视图只关心成功后做什么（刷新列表、关闭面板）。
 * 失败一律由 http 拦截器提示，这里不重复渲染文案。
 */
export function useReviewActions() {
  const { submitting, run } = useSubmit()

  return {
    submitting,
    approvePost: (postId: number) => run(() => approvePost(postId)),
    rejectPost: (postId: number, payload: ReasonPayload) => run(() => rejectPost(postId, payload)),
    takeDownPost: (postId: number, payload: ProposalDispositionPayload) =>
      run(() => takeDownPost(postId, payload)),
    restorePost: (postId: number, payload: ReasonPayload) => run(() => restorePost(postId, payload)),
    hidePostContact: (postId: number, payload: ProposalDispositionPayload) =>
      run(() => hidePostContact(postId, payload)),
    showPostContact: (postId: number, payload: ReasonPayload) =>
      run(() => showPostContact(postId, payload)),
    removeComment: (commentId: number, payload: ProposalDispositionPayload) =>
      run(() => removeComment(commentId, payload)),
    dismissReports: (payload: DismissReportsPayload) => run(() => dismissReports(payload)),
    upholdReports: (postId: number, payload: ProposalDispositionPayload) =>
      run(() => upholdReports(postId, payload)),
    acceptAppeal: (appealId: number, payload: ReasonPayload) => run(() => acceptAppeal(appealId, payload)),
    rejectAppeal: (appealId: number, payload: ReasonPayload) => run(() => rejectAppeal(appealId, payload)),
  }
}
