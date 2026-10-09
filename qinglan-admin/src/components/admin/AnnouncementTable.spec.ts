import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AnnouncementTable from '@/components/admin/AnnouncementTable.vue'
import type { AdminAnnouncementItem } from '@/types/admin'

function announcement(overrides: Partial<AdminAnnouncementItem> = {}): AdminAnnouncementItem {
  return {
    id: 1,
    title: '系统维护通知',
    level: 'URGENT',
    status: 'DRAFT',
    startAt: '2026-10-10T00:00:00Z',
    endAt: null,
    minReadSeconds: 10,
    requireScrollToEnd: true,
    requireConfirm: false,
    publishedAt: null,
    revokedAt: null,
    createdAt: '2026-10-08T00:00:00Z',
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

describe('AnnouncementTable', () => {
  it('空列表渲染空态提示', () => {
    expect(mount(AnnouncementTable, { props: { items: [] } }).text()).toContain('还没有公告')
  })

  it('发布时间渲染相对时间，绝对时间落在 title 上', () => {
    const publishedAt = new Date().toISOString()
    const wrapper = mount(AnnouncementTable, { props: { items: [announcement({ publishedAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(publishedAt).toLocaleString())

    expect(cell?.text()).toBe('刚刚')
  })

  it('表头固定：thead 带 sticky + 不透明底色', () => {
    const thead = mount(AnnouncementTable, { props: { items: [announcement()] } }).find('thead')

    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('等级与状态渲染成中文标签，不泄漏后端枚举', () => {
    const wrapper = mount(AnnouncementTable, {
      props: { items: [announcement({ level: 'URGENT', status: 'DRAFT' })] },
    })
    const text = wrapper.text()

    expect(text).toContain('紧急')
    expect(text).toContain('草稿')
    expect(text).not.toContain('URGENT')
    expect(text).not.toContain('DRAFT')
  })

  it('DRAFT 行给「发布」，PUBLISHED 行给「撤回」', () => {
    const wrapper = mount(AnnouncementTable, {
      props: {
        items: [announcement({ id: 1, status: 'DRAFT' }), announcement({ id: 2, status: 'PUBLISHED' })],
        canOperate: true,
      },
    })

    expect(buttonByText(wrapper, '发布').exists()).toBe(true)
    expect(buttonByText(wrapper, '撤回').exists()).toBe(true)
  })

  it('REVOKED 行不再给任何操作按钮', () => {
    const wrapper = mount(AnnouncementTable, {
      props: { items: [announcement({ id: 3, status: 'REVOKED' })], canOperate: true },
    })

    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('点击「发布」「撤回」分别抛出该行数据', async () => {
    const draft = announcement({ id: 1, status: 'DRAFT' })
    const published = announcement({ id: 2, status: 'PUBLISHED' })
    const wrapper = mount(AnnouncementTable, {
      props: { items: [draft, published], canOperate: true },
    })

    await buttonByText(wrapper, '发布').trigger('click')
    await buttonByText(wrapper, '撤回').trigger('click')

    expect(wrapper.emitted('publish')).toEqual([[draft]])
    expect(wrapper.emitted('revoke')).toEqual([[published]])
  })

  it('非超级管理员时发布与撤回按钮禁用', () => {
    const wrapper = mount(AnnouncementTable, {
      props: {
        items: [announcement({ id: 1, status: 'DRAFT' }), announcement({ id: 2, status: 'PUBLISHED' })],
      },
    })

    expect(buttonByText(wrapper, '发布').attributes('disabled')).toBeDefined()
    expect(buttonByText(wrapper, '撤回').attributes('disabled')).toBeDefined()
  })
})
