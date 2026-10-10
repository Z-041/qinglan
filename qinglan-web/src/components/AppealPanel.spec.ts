import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppealPanel from './AppealPanel.vue'

function mountPanel(props: Record<string, unknown> = {}) {
  return mount(AppealPanel, { props: { reviewReason: null, appealed: false, ...props } })
}

describe('AppealPanel', () => {
  it('展示下架提示；无处置原因时不渲染原因行', () => {
    const wrapper = mountPanel()
    expect(wrapper.text()).toContain('内容已下架，他人不可见')
    expect(wrapper.text()).not.toContain('处置原因')
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('有处置原因时展示：违规码被映射成中文标签', () => {
    // reviewReasonText 把 `CODE: 详情` 折算为「中文标签：详情」，不直接暴露后端枚举
    const wrapper = mountPanel({ reviewReason: 'ABUSE: 人身攻击' })
    expect(wrapper.text()).toContain('处置原因')
    expect(wrapper.text()).toContain('人身攻击')
    expect(wrapper.text()).not.toContain('ABUSE')
  })

  it('理由为空时提交按钮禁用，填写后可提交并抛出理由', async () => {
    const wrapper = mountPanel()
    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeDefined()

    await wrapper.get('textarea').setValue('  我是原作者，内容无违规  ')
    expect(button.attributes('disabled')).toBeUndefined()

    await button.trigger('click')
    expect(wrapper.emitted('submit')).toEqual([['我是原作者，内容无违规']])
  })

  it('已申诉时只显示提示，不再渲染输入框', () => {
    const wrapper = mountPanel({ appealed: true })
    expect(wrapper.text()).toContain('申诉已提交，将由另一位管理员复审')
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('提交中时按钮进入在途态', () => {
    const wrapper = mountPanel({ submitting: true })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })
})
