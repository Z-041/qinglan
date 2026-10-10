import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import PageHeader from './PageHeader.vue'

describe('PageHeader', () => {
  it('渲染标题', () => {
    const wrapper = mount(PageHeader, { props: { title: '审核队列' } })
    expect(wrapper.get('h1').text()).toBe('审核队列')
  })

  it('不传 refreshing 时不渲染刷新按钮，点击事件无来源', () => {
    const wrapper = mount(PageHeader, { props: { title: '测试' } })
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('传 refreshing 时渲染刷新按钮，在途转圈且点击抛 refresh', async () => {
    const wrapper = mount(PageHeader, { props: { title: '测试', refreshing: false } })
    const button = wrapper.get('button')
    expect(button.text()).toBe('刷新')

    await button.trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    await wrapper.setProps({ refreshing: true })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('#extra 插槽渲染在刷新按钮之前', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '测试', refreshing: false },
      slots: { extra: '<button type="button">导出 CSV</button>' },
    })
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('导出 CSV')
    expect(buttons[1].text()).toBe('刷新')
  })

  it('只有 extra、无 refreshing 时右侧仍渲染插槽', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '测试' },
      slots: { extra: '<button type="button">新建公告</button>' },
    })
    expect(wrapper.get('button').text()).toBe('新建公告')
  })
})
