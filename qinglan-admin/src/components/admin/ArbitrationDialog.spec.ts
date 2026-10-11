import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ArbitrationDialog from '@/components/admin/ArbitrationDialog.vue'

function mountDialog() {
  return mount(ArbitrationDialog, { props: { open: true } })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('ArbitrationDialog', () => {
  it('理由为空时点「准予处置」不提交，并提示必填', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '准予处置').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('请填写裁决理由')
  })

  it('理由只有空白同样视为未填，不提交', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('   ')

    await buttonByText(wrapper, '不予处置').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('请填写裁决理由')
  })

  it('填了理由点「准予处置」提交 GRANT，理由按 trim 后的值上报', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('  证据充分，准予下架  ')

    await buttonByText(wrapper, '准予处置').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ decision: 'GRANT', reason: '证据充分，准予下架' }]])
  })

  it('填了理由点「不予处置」提交 DENY', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('证据不足')

    await buttonByText(wrapper, '不予处置').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ decision: 'DENY', reason: '证据不足' }]])
  })

  it('重新打开时回到初始态，不残留上一轮的理由与错误提示', async () => {
    const wrapper = mountDialog()
    await buttonByText(wrapper, '准予处置').trigger('click')
    await wrapper.get('textarea').setValue('上一轮的理由')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    expect(wrapper.text()).not.toContain('请填写裁决理由')
  })

  it('点取消抛出 close', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '取消').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
