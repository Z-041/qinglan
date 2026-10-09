import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReviewContextPanel from '@/components/admin/ReviewContextPanel.vue'
import type { AdminReviewContext, AdminReviewHistory } from '@/types/review'

/** 抽屉走 Teleport，单测换成直接渲染内容的桩 */
const ElDrawerStub = {
  props: ['modelValue', 'title'],
  template: '<div v-if="modelValue"><slot /></div>',
}

function context(): AdminReviewContext {
  return {
    targetType: 'POST',
    targetId: 5,
    targetStatus: 'TAKEN_DOWN',
    authorCreditScore: 88,
    authorViolationCount: 2,
    reportCount: 3,
    distinctReporterCount: 3,
    repeatedReporterCount: 1,
    reasons: ['ABUSE'],
  }
}

function history(): AdminReviewHistory {
  return {
    authorRecentPosts: [{ id: 12, status: 'REJECTED', createdAt: '2026-10-01T00:00:00Z' }],
    targetDispositions: [
      { action: 'POST_TAKEDOWN', operatorId: 7, reason: '辱骂', createdAt: '2026-10-02T00:00:00Z' },
      { action: 'POST_AUTO_TAKEDOWN', operatorId: 0, reason: null, createdAt: '2026-10-02T01:00:00Z' },
    ],
  }
}

function mountPanel(props: Record<string, unknown>) {
  return mount(ReviewContextPanel, {
    props: { open: true, context: context(), history: history(), ...props },
    global: { stubs: { ElDrawer: ElDrawerStub } },
  })
}

describe('ReviewContextPanel', () => {
  it('概况 Tab 展示既有聚合信息', () => {
    const wrapper = mountPanel({})

    expect(wrapper.text()).toContain('作者信用分')
    expect(wrapper.text()).toContain('88')
  })

  it('作者动态 Tab 展示帖子 ID 与状态，不出现身份明文', () => {
    const wrapper = mountPanel({})

    expect(wrapper.text()).toContain('帖子 #12')
    expect(wrapper.text()).toContain('已驳回')
    // 只回 ID / 状态 / 时间：不含学号邮箱
    expect(wrapper.text()).not.toContain('@')
  })

  it('处置历史 Tab 展示动作、操作人 ID 与理由；系统处置的 0 显示为「系统」', () => {
    const wrapper = mountPanel({})

    expect(wrapper.text()).toContain('下架帖子')
    expect(wrapper.text()).toContain('操作人：#7')
    expect(wrapper.text()).toContain('自动下架（举报达阈值）')
    expect(wrapper.text()).toContain('操作人：系统')
  })

  it('历史加载失败给出说明文案（区分「加载失败」与「确实没有」）', () => {
    const wrapper = mountPanel({ history: null, historyFailed: true })

    expect(wrapper.text()).toContain('作者动态没能加载出来')
  })

  it('历史为空时给出「暂无」占位', () => {
    const wrapper = mountPanel({
      history: { authorRecentPosts: [], targetDispositions: [] },
    })

    expect(wrapper.text()).toContain('暂无近期动态')
    expect(wrapper.text()).toContain('暂无处置记录')
  })
})
