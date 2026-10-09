import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import ProposalReasonDialog from '@/components/admin/ProposalReasonDialog.vue'

/**
 * el-dialog 走 Teleport 且懒渲染，单测里换成桩；桩要保留 `modelValue` / `update:modelValue`
 * 这条唯一通道，否则会出现「单测全绿、浏览器里弹窗关不掉」的盲区。
 */
const ElDialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}

function mountDialog(confirmText = '发起下架提案') {
  return mount(ProposalReasonDialog, {
    props: { open: true, title: '发起下架提案', confirmText },
    global: { stubs: { ElDialog: ElDialogStub } },
  })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('ProposalReasonDialog', () => {
  it('默认按「辱骂」发起提案，说明留空时为 null', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '发起下架提案').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ reasonCode: 'ABUSE', detail: null }]])
  })

  it('选类型并填说明时按 trim 后的值上报', async () => {
    const wrapper = mountDialog()
    await wrapper.get('select').setValue('FRAUD')
    await wrapper.get('textarea').setValue('  诈骗  ')

    await buttonByText(wrapper, '发起下架提案').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ reasonCode: 'FRAUD', detail: '诈骗' }]])
  })

  it('只填空白视为没填：detail 转 null 而不是空格', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('   ')

    await buttonByText(wrapper, '发起下架提案').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{ reasonCode: 'ABUSE', detail: null }]])
  })

  it('确认按钮文案由 confirmText 决定（四个动作复用同一弹窗）', () => {
    const wrapper = mountDialog('发起移除评论提案')

    expect(wrapper.text()).toContain('发起移除评论提案')
  })

  it('重新打开时回到初始态，不残留上一轮的类型与说明', async () => {
    const wrapper = mountDialog()
    await wrapper.get('select').setValue('PRIVACY')
    await wrapper.get('textarea').setValue('上一轮的说明')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('ABUSE')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  })

  it('提交中时取消按钮禁用，避免提案请求在途时关掉弹窗', () => {
    const wrapper = mount(ProposalReasonDialog, {
      props: { open: true, title: '发起下架提案', confirmText: '发起下架提案', submitting: true },
      global: { stubs: { ElDialog: ElDialogStub } },
    })

    expect(buttonByText(wrapper, '取消').element.disabled).toBe(true)
  })

  it('把 open 透传给 el-dialog，并响应它抛出的关闭事件', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(ElDialogStub)

    expect(dialog.props('modelValue')).toBe(true)

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('弹窗内 Enter 确认（多行说明之外的区域直接提交）', async () => {
    const wrapper = mountDialog()

    await wrapper.get('.gap-4').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('submit')).toEqual([[{ reasonCode: 'ABUSE', detail: null }]])
  })
})
