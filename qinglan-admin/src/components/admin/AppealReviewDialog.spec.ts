import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppealReviewDialog from '@/components/admin/AppealReviewDialog.vue'

function mountDialog() {
  return mount(AppealReviewDialog, { props: { open: true } })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button
}

describe('AppealReviewDialog', () => {
  it('单选组用 fieldset/legend 承载，不能嵌进 label 里', () => {
    const wrapper = mountDialog()
    const radios = wrapper.findAll('fieldset input[type="radio"]')

    expect(radios).toHaveLength(2)
    expect(wrapper.get('fieldset legend').text()).toBe('复审结论')

    // 关键结构断言：radio 的最近 label 祖先之上不能再有 label。
    // 若用 FormField（根是 <label>）包住这个单选组，就会出现嵌套 label，
    // 而 HTML 规定无 for 的 label 关联第一个 labelable 后代 → 点分组标题即把结论改成「通过申诉」。
    // jsdom 不实现 label 的激活行为，故此缺陷只能靠结构断言钉住。
    for (const radio of radios) {
      const ownLabel = radio.element.closest('label')
      expect(ownLabel?.tagName).toBe('LABEL')
      // 注意 closest() 会匹配自身，所以要从父节点往上找：这样才是在判断「label 之外还有没有 label」
      expect(ownLabel?.parentElement?.closest('label')).toBeNull()
    }
  })

  it('复审说明为空时不能提交，填写后可提交', async () => {
    const wrapper = mountDialog()
    const submit = buttonByText(wrapper, '提交复审')

    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('textarea').setValue('依据不足，维持原判')

    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('默认结论是通过申诉，提交时按所选结论与去空白的说明上报', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('  证据充分  ')

    await buttonByText(wrapper, '提交复审').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([true, '证据充分'])
  })

  it('选择维持原判后按 false 上报', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('维持原判')
    await wrapper.findAll('input[type="radio"]')[1].setValue()

    await buttonByText(wrapper, '提交复审').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([false, '维持原判'])
  })

  it('点取消抛出 close', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '取消').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('打开时按 initialApproved 预置结论（快捷键 2 = 维持原判）', async () => {
    const wrapper = mount(AppealReviewDialog, {
      props: { open: false, initialApproved: false },
    })
    await wrapper.setProps({ open: true })
    await wrapper.get('textarea').setValue('依据不足')

    await buttonByText(wrapper, '提交复审').trigger('click')

    expect(wrapper.emitted('submit')?.[0]).toEqual([false, '依据不足'])
  })

  it('弹窗内 Enter 确认：多行输入区之外触发提交，textarea 内的 Enter 不触发', async () => {
    const wrapper = mountDialog()
    await wrapper.get('textarea').setValue('证据充分')

    // textarea 里的 Enter 是换行，不能当确认
    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('submit')).toBeUndefined()

    // 其余位置（如结论单选组）Enter 即确认
    await wrapper.get('.gap-4').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('submit')?.[0]).toEqual([true, '证据充分'])
  })
})
