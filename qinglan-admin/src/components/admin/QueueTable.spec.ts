import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import QueueTable from '@/components/admin/QueueTable.vue'
import type { AdminQueueItem } from '@/types/review'

function item(overrides: Partial<AdminQueueItem> = {}): AdminQueueItem {
  return {
    targetType: 'POST',
    targetId: 5,
    preview: '测试正文',
    images: [],
    targetStatus: 'PENDING',
    reportCount: 2,
    firstReportedAt: '2026-10-01T00:00:00Z',
    reasons: ['ABUSE', 'FRAUD'],
    details: [],
    proposal: null,
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
    // 按「渲染出的举报数单元格」定位，而不是断言整段文本里出现过「2」——
    // 日期时间等也含数字，那种零头断言等于什么都没验证
    expect(wrapper.findAll('td').some((cell) => cell.text() === '2')).toBe(true)
    expect(text).toContain('辱骂、人身攻击')
    expect(text).toContain('诈骗与欺诈')
  })

  it('渲染举报人填写的补充说明：理由选「其他」时这是唯一线索', () => {
    const wrapper = mount(QueueTable, {
      props: { items: [item({ reasons: ['OTHER'], details: ['疑似中介在拉群', '还发了外链'] })] },
    })

    expect(wrapper.text()).toContain('其他')
    expect(wrapper.text()).toContain('疑似中介在拉群')
    expect(wrapper.text()).toContain('还发了外链')
  })

  it('渲染缩略图：按序渲染全部图片，点开在新标签页看原图', () => {
    const wrapper = mount(QueueTable, {
      props: { items: [item({ images: ['/uploads/a.jpg', '/uploads/b.jpg'] })] },
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
    const wrapper = mount(QueueTable, { props: { items: [item()] } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.findAll('td').some((cell) => cell.text() === '—')).toBe(true)
  })

  it('首报超过 24 小时的行标红', () => {
    const firstReportedAt = '2000-01-01T00:00:00Z'
    const wrapper = mount(QueueTable, { props: { items: [item({ firstReportedAt })] } })

    // 时间列改为相对时间后，按绝对时间（title）定位单元格，而不是写死 td[4]：
    // 列序一旦增删，写死的下标会指向别的列，断言就失去意义了
    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(firstReportedAt).toLocaleString())

    expect(cell).toBeDefined()
    expect(cell?.classes()).toContain('text-danger-deep')
  })

  it('首报在 24 小时内的行不标红', () => {
    const firstReportedAt = new Date().toISOString()
    const wrapper = mount(QueueTable, { props: { items: [item({ firstReportedAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(firstReportedAt).toLocaleString())

    expect(cell).toBeDefined()
    expect(cell?.classes()).not.toContain('text-danger-deep')
  })

  it('时间列渲染相对时间，绝对时间放在 title 悬停可见', () => {
    const firstReportedAt = new Date().toISOString()
    const wrapper = mount(QueueTable, { props: { items: [item({ firstReportedAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(firstReportedAt).toLocaleString())

    expect(cell?.text()).toBe('刚刚')
  })

  it('表头固定：thead 带 sticky + 不透明底色', () => {
    const wrapper = mount(QueueTable, { props: { items: [item()] } })
    const thead = wrapper.find('thead')

    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('点击「处置」抛出该行数据', async () => {
    const row = item()
    const wrapper = mount(QueueTable, { props: { items: [row] } })

    await buttonByText(wrapper, '处置').trigger('click')

    expect(wrapper.emitted('handle')).toEqual([[row]])
  })

  it('有进行中提案时行内显示进度与「我是否已投」，避免撞 409 后无处可去', () => {
    const wrapper = mount(QueueTable, {
      props: {
        items: [
          item({
            proposal: {
              proposalId: 12,
              action: 'TAKE_DOWN',
              status: 'OPEN',
              agreeCount: 2,
              needed: 4,
              votedCount: 3,
              myDecision: 'AGREE',
            },
          }),
        ],
      },
    })

    const text = wrapper.text()
    expect(text).toContain('下架帖子')
    expect(text).toContain('2/4 同意')
    expect(text).toContain('我已投')
    expect(text).toContain('表决中')
  })

  it('我已投反对时标注「我已投」，未投票时标注「我未投」', () => {
    const base = {
      proposalId: 12,
      action: 'TAKE_DOWN' as const,
      status: 'OPEN' as const,
      agreeCount: 1,
      needed: 4,
      votedCount: 1,
    }

    const voted = mount(QueueTable, {
      props: { items: [item({ proposal: { ...base, myDecision: 'DISAGREE' } })] },
    })
    const notVoted = mount(QueueTable, {
      props: { items: [item({ proposal: { ...base, myDecision: null } })] },
    })

    expect(voted.text()).toContain('我已投')
    expect(notVoted.text()).toContain('我未投')
  })

  it('僵局提案直接标出「待超管裁决」，让人不再等票', () => {
    const wrapper = mount(QueueTable, {
      props: {
        items: [
          item({
            proposal: {
              proposalId: 12,
              action: 'TAKE_DOWN',
              status: 'ESCALATED',
              agreeCount: 1,
              needed: 3,
              votedCount: 4,
              myDecision: 'AGREE',
            },
          }),
        ],
      },
    })

    expect(wrapper.text()).toContain('僵局待超管裁决')
  })

  it('没有进行中提案时该列显示占位符，不显示「表决中」', () => {
    const wrapper = mount(QueueTable, { props: { items: [item()] } })

    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).not.toContain('表决中')
  })

  it('按 activeKey 高亮键盘焦点行，其余行不高亮', () => {
    const wrapper = mount(QueueTable, {
      props: {
        items: [item({ targetId: 5 }), item({ targetId: 6 })],
        activeKey: 'POST-6',
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].classes()).not.toContain('bg-primary-soft')
    expect(rows[1].classes()).toContain('bg-primary-soft')
  })
})
