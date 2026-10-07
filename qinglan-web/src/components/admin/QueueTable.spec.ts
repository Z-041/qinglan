import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import QueueTable from '@/components/admin/QueueTable.vue'
import type { AdminQueueItem } from '@/types/review'

function item(overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return {
    targetType: 'POST',
    targetId: 5,
    preview: '测试正文',
    targetStatus: 'PENDING',
    reportCount: 2,
    firstReportedAt: '2026-10-01T00:00:00Z',
    reasons: ['ABUSE', 'FRAUD'],
    ...overrides,
  }
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('QueueTable', () => {
  it('空列表渲染空态提示', () => {
    const wrapper = mount(QueueTable, { props: { items: [] } })

    expect(wrapper.text()).toContain('暂无待处理举报')
  })

  it('渲染摘要、状态、举报数与理由文案', () => {
    const wrapper = mount(QueueTable, { props: { items: [item()] } })
    const text = wrapper.text()

    expect(text).toContain('帖子')
    expect(text).toContain('测试正文')
    expect(text).toContain('待审核')
    expect(text).toContain('2')
    expect(text).toContain('辱骂、人身攻击')
    expect(text).toContain('诈骗与欺诈')
  })

  it('首报超过 24 小时的行标红', () => {
    const firstReportedAt = '2000-01-01T00:00:00Z'
    const wrapper = mount(QueueTable, { props: { items: [item({ firstReportedAt })] } })

    // 按「渲染出来的首报时间」定位单元格，而不是写死 td[4]：
    // 列序一旦增删，写死的下标会指向别的列，断言就失去意义了
    const cell = wrapper
      .findAll('td')
      .find((element) => element.text() === new Date(firstReportedAt).toLocaleString())

    expect(cell).toBeDefined()
    expect(cell?.classes()).toContain('text-danger-deep')
  })

  it('首报在 24 小时内的行不标红', () => {
    const firstReportedAt = new Date().toISOString()
    const wrapper = mount(QueueTable, { props: { items: [item({ firstReportedAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.text() === new Date(firstReportedAt).toLocaleString())

    expect(cell).toBeDefined()
    expect(cell?.classes()).not.toContain('text-danger-deep')
  })

  it('点击「处置」抛出该行数据', async () => {
    const row = item()
    const wrapper = mount(QueueTable, { props: { items: [row] } })

    await buttonByText(wrapper, '处置').trigger('click')

    expect(wrapper.emitted('handle')).toEqual([[row]])
  })
})
