import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UserTable from '@/components/admin/UserTable.vue'
import type { AdminUserItem } from '@/types/admin'

function user(overrides: Partial<AdminUserItem> = {}): AdminUserItem {
  return {
    id: 987654,
    role: 'STUDENT',
    status: 'ACTIVE',
    creditScore: 88,
    bannedUntil: null,
    createdAt: '2026-10-01T12:00:00Z',
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

describe('UserTable', () => {
  it('空列表渲染空态提示', () => {
    const wrapper = mount(UserTable, { props: { items: [] } })

    expect(wrapper.text()).toContain('没有符合条件的用户')
  })

  it('角色与状态渲染成中文标签，信用分原样展示', () => {
    const wrapper = mount(UserTable, { props: { items: [user()] } })
    const text = wrapper.text()

    expect(text).toContain('学生')
    expect(text).toContain('正常')
    expect(text).toContain('88')
  })

  it('决策 6：表格不渲染用户数字 ID', () => {
    const wrapper = mount(UserTable, { props: { items: [user()] } })

    expect(wrapper.text()).not.toContain('987654')
  })

  it('注册时间渲染相对时间，绝对时间落在 title 上', () => {
    const createdAt = new Date().toISOString()
    const wrapper = mount(UserTable, { props: { items: [user({ createdAt })] } })

    const cell = wrapper
      .findAll('td')
      .find((element) => element.attributes('title') === new Date(createdAt).toLocaleString())

    expect(cell?.text()).toBe('刚刚')
  })

  it('表头固定：thead 带 sticky + 不透明底色', () => {
    const thead = mount(UserTable, { props: { items: [user()] } }).find('thead')

    expect(thead.classes()).toContain('sticky')
    expect(thead.classes()).toContain('bg-surface')
  })

  it('封禁中的用户只给「解封」，并展示封禁截止时间', () => {
    const wrapper = mount(UserTable, {
      props: { items: [user({ status: 'BANNED', bannedUntil: '2026-12-01T00:00:00Z' })] },
    })

    expect(wrapper.text()).toContain('已封禁')
    expect(buttonByText(wrapper, '解封').exists()).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text() === '封禁')).toBe(false)
  })

  it('点击「封禁」「调分」分别抛出该行数据', async () => {
    const row = user()
    const wrapper = mount(UserTable, { props: { items: [row] } })

    await buttonByText(wrapper, '封禁').trigger('click')
    await buttonByText(wrapper, '调分').trigger('click')

    expect(wrapper.emitted('ban')).toEqual([[row]])
    expect(wrapper.emitted('credit')).toEqual([[row]])
  })
})
