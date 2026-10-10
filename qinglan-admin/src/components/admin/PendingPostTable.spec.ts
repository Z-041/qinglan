import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PendingPostTable from '@/components/admin/PendingPostTable.vue'
import type { AdminPendingPostItem } from '@/types/review'

function pendingPost(overrides: Partial<AdminPendingPostItem> = {}): AdminPendingPostItem {
  return {
    postId: 5,
    preview: '测试正文',
    images: [],
    targetStatus: 'PENDING',
    submittedAt: '2026-10-06T00:00:00Z',
    reportCount: 0,
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

describe('PendingPostTable', () => {
  it('空列表渲染空态提示', () => {
    const wrapper = mount(PendingPostTable, { props: { items: [] } })

    expect(wrapper.text()).toContain('暂无待审帖子')
  })

  it('加载中与加载失败的空态文案不同，不与「暂无」混为一谈', () => {
    const loading = mount(PendingPostTable, { props: { items: [], loading: true } })
    const failed = mount(PendingPostTable, { props: { items: [], failed: true } })

    expect(loading.text()).toContain('加载中')
    expect(failed.text()).toContain('加载失败')
  })

  it('渲染摘要、状态码对应文案与提交时间', () => {
    const submittedAt = '2026-10-06T00:00:00Z'
    const wrapper = mount(PendingPostTable, {
      props: { items: [pendingPost({ targetStatus: 'REVIEWING', submittedAt })] },
    })
    const text = wrapper.text()

    expect(text).toContain('测试正文')
    expect(text).toContain('审核中')
    // 提交时间由 RelativeTime 渲染：time[datetime] + 绝对 title（相对文案随当前时刻变化，不断言字面）
    expect(wrapper.find(`time[datetime="${submittedAt}"]`).exists()).toBe(true)
  })

  it('表头固定：thead 带 sticky + 不透明底色', () => {
    const thead = mount(PendingPostTable, { props: { items: [pendingPost()] } }).find('thead')

    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('渲染缩略图：按序渲染全部图片，点开在新标签页看原图', () => {
    const wrapper = mount(PendingPostTable, {
      props: { items: [pendingPost({ images: ['/uploads/a.jpg', '/uploads/b.jpg'] })] },
    })

    const imgs = wrapper.findAll('img')
    expect(imgs.map((img) => img.attributes('src'))).toEqual(['/uploads/a.jpg', '/uploads/b.jpg'])
    expect(imgs[0].classes()).toContain('object-cover')

    const links = wrapper.findAll('a')
    expect(links).toHaveLength(2)
    expect(links[0].attributes('href')).toBe('/uploads/a.jpg')
    expect(links[0].attributes('target')).toBe('_blank')
    expect(links[0].attributes('rel')).toBe('noopener noreferrer')
  })

  it('无图时不渲染 img，图片列显示占位符而不是留空', () => {
    const wrapper = mount(PendingPostTable, { props: { items: [pendingPost()] } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('td').some((cell) => cell.text() === '—')).toBe(true)
  })

  it('不变量 #1：没有作者列，作者身份不出队列', () => {
    const wrapper = mount(PendingPostTable, { props: { items: [pendingPost()] } })

    expect(wrapper.text()).not.toContain('作者')
    expect(wrapper.text()).not.toContain('匿名代号')
  })

  it('有举报时把举报数标红，无举报展示 0', () => {
    const withReports = mount(PendingPostTable, { props: { items: [pendingPost({ reportCount: 3 })] } })
    const withoutReports = mount(PendingPostTable, { props: { items: [pendingPost()] } })

    expect(withReports.text()).toContain('3')
    expect(withReports.find('.text-danger-deep').exists()).toBe(true)
    expect(withoutReports.find('.text-danger-deep').exists()).toBe(false)
  })

  it('点击「审核」抛出该行数据', async () => {
    const row = pendingPost()
    const wrapper = mount(PendingPostTable, { props: { items: [row] } })

    await buttonByText(wrapper, '审核').trigger('click')

    expect(wrapper.emitted('select')).toEqual([[row]])
  })
})
