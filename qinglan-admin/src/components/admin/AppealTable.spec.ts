import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppealTable from '@/components/admin/AppealTable.vue'
import type { AdminAppealItem } from '@/types/review'

function appeal(overrides: Partial<AdminAppealItem> = {}): AdminAppealItem {
  return {
    appealId: 987654,
    targetType: 'POST',
    targetId: 5,
    preview: '测试正文',
    targetStatus: 'TAKEN_DOWN',
    takenDownReason: 'ABUSE: 人身攻击',
    appealReason: '当时是引用新闻',
    appealedAt: '2026-10-01T00:00:00Z',
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

describe('AppealTable', () => {
  it('空列表渲染空态提示', () => {
    const wrapper = mount(AppealTable, { props: { items: [] } })

    expect(wrapper.text()).toContain('暂无待复审申诉')
  })

  it('申诉时间渲染相对时间，绝对时间落在 title 上', () => {
    const appealedAt = new Date().toISOString()
    const wrapper = mount(AppealTable, { props: { items: [appeal({ appealedAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(appealedAt).toLocaleString())

    expect(cell?.text()).toBe('刚刚')
  })

  it('表头固定：thead 带 sticky + 不透明底色', () => {
    const thead = mount(AppealTable, { props: { items: [appeal()] } }).find('thead')

    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('渲染摘要、状态、原下架理由文案与申诉理由', () => {
    const wrapper = mount(AppealTable, { props: { items: [appeal()] } })
    const text = wrapper.text()

    expect(text).toContain('测试正文')
    expect(text).toContain('已下架')
    expect(text).toContain('辱骂、人身攻击：人身攻击')
    expect(text).toContain('当时是引用新闻')
  })

  it('不变量 #1：没有申诉人列，也不渲染 appealId', () => {
    const wrapper = mount(AppealTable, { props: { items: [appeal()] } })

    expect(wrapper.text()).not.toContain('申诉人')
    expect(wrapper.text()).not.toContain('987654')
  })

  it('帖子已被删除时摘要与理由降级展示', () => {
    const wrapper = mount(AppealTable, {
      props: { items: [appeal({ preview: null, targetStatus: null, takenDownReason: null })] },
    })

    expect(wrapper.text()).toContain('内容已不存在')
    expect(wrapper.text()).toContain('已删除')
  })

  it('点击「复审」抛出该行数据', async () => {
    const row = appeal()
    const wrapper = mount(AppealTable, { props: { items: [row] } })

    await buttonByText(wrapper, '复审').trigger('click')

    expect(wrapper.emitted('review')).toEqual([[row]])
  })
})
