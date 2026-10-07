import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ReportDialog from './ReportDialog.vue'

function mountDialog(open = true): VueWrapper {
  return mount(ReportDialog, { props: { open, label: '这条帖子' } })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((item) => item.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('ReportDialog', () => {
  it('未打开时不渲染弹层', () => {
    expect(mountDialog(false).find('[role="dialog"]').exists()).toBe(false)
  })

  it('展示全部预设理由，未选理由时不能提交', async () => {
    const wrapper = mountDialog()

    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(8)
    expect(buttonByText(wrapper, '提交举报').element.disabled).toBe(true)

    await wrapper.get('input[value="ABUSE"]').setValue()

    expect(buttonByText(wrapper, '提交举报').element.disabled).toBe(false)
  })

  it('提交时抛出所选理由与补充说明', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[value="FRAUD"]').setValue()
    await wrapper.get('textarea').setValue('收款后拉黑')

    await buttonByText(wrapper, '提交举报').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([['FRAUD', '收款后拉黑']])
  })

  it('重新打开时清空上一轮的选择与说明', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[value="ABUSE"]').setValue()
    await wrapper.get('textarea').setValue('上一轮的说明')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect(buttonByText(wrapper, '提交举报').element.disabled).toBe(true)
    expect(wrapper.get('textarea').element.value).toBe('')
  })

  it('点关闭按钮抛出 close', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '关闭').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('提交中时关闭与取消均被禁用，避免请求悬空', () => {
    const wrapper = mount(ReportDialog, {
      props: { open: true, label: '这条评论', submitting: true },
    })

    // 头部「关闭」也要断言：此前它没有 disabled，测试名宣称覆盖了、断言里却漏了它
    expect(buttonByText(wrapper, '关闭').element.disabled).toBe(true)
    expect(buttonByText(wrapper, '取消').element.disabled).toBe(true)
    expect(buttonByText(wrapper, '提交举报').element.disabled).toBe(true)
  })

  it('提交中即使绕过 disabled 触发关闭也不生效（唯一出口统一判 submitting）', async () => {
    const wrapper = mount(ReportDialog, {
      props: { open: true, label: '这条评论', submitting: true },
    })

    await wrapper.get('[role="dialog"]').trigger('click')
    await wrapper.get('[role="dialog"]').trigger('keydown.escape')

    expect(wrapper.emitted('close')).toBeUndefined()
  })
})
