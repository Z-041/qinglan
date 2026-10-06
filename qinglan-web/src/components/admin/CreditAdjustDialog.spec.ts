import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CreditAdjustDialog from '@/components/admin/CreditAdjustDialog.vue'

/**
 * el-dialog 默认走 Teleport 且懒渲染，单测里换成只渲染插槽的桩，
 * 聚焦表单逻辑本身（本 spec 要盯的就是「填完值按钮能不能点」）。
 */
const ElDialogStub = { template: '<div><slot /><slot name="footer" /></div>' }

function mountDialog() {
  return mount(CreditAdjustDialog, {
    props: { open: true },
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

describe('CreditAdjustDialog', () => {
  it('填入范围内非 0 的整数与理由后可提交，emit submit(分值, 理由)', async () => {
    const wrapper = mountDialog()

    await wrapper.get('input[type="number"]').setValue('10')
    await wrapper.get('textarea').setValue('灌水扣分')
    await buttonByText(wrapper, '确认调整').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[10, '灌水扣分']])
  })

  it('分值为 0、超范围或非整数时按钮保持禁用', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('理由')

    for (const value of ['0', '101', '-101', '1.5']) {
      await wrapper.get('input[type="number"]').setValue(value)
      expect(buttonByText(wrapper, '确认调整').attributes('disabled')).toBeDefined()
    }
  })

  it('分值与理由都为空时按钮禁用', () => {
    const wrapper = mountDialog()

    expect(buttonByText(wrapper, '确认调整').attributes('disabled')).toBeDefined()
  })
})
