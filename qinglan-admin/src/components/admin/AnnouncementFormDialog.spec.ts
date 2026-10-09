import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import AnnouncementFormDialog from '@/components/admin/AnnouncementFormDialog.vue'

/**
 * el-dialog 默认走 Teleport 且懒渲染，单测里换成只渲染插槽的桩，聚焦表单逻辑本身。
 *
 * 桩**必须保留 `modelValue` / `update:modelValue` 这条唯一通道**：整体 stub 掉却丢掉它，
 * 会出现「单测全绿、浏览器里弹窗关不掉」的盲区——组件的开合契约从没被验证过。
 */
const ElDialogStub = {
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot /><slot name="footer" /></div>',
}

function mountDialog() {
  return mount(AnnouncementFormDialog, {
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

/** 生效开始与生效结束都是 datetime-local，取第一个即「生效开始」 */
function inputByType(wrapper: VueWrapper, type: string): DOMWrapper<HTMLInputElement> {
  const input = wrapper.findAll('input').find((element) => element.attributes('type') === type)
  if (!input) {
    throw new Error(`找不到输入框：${type}`)
  }
  return input
}

describe('AnnouncementFormDialog', () => {
  it('标题、正文、生效开始任一为空时提交按钮保持禁用', async () => {
    const wrapper = mountDialog()
    const submit = buttonByText(wrapper, '提交草稿')

    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('input[type="text"]').setValue('停水通知')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    expect(submit.attributes('disabled')).toBeDefined()

    await inputByType(wrapper, 'datetime-local').setValue('2026-10-08T10:00')
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('提交时生效时间转 ISO-8601，最短阅读秒数留空传 null', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[type="text"]').setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    await inputByType(wrapper, 'datetime-local').setValue('2026-10-08T10:00')

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      title: '停水通知',
      content: '今晚 22:00 起停水',
      level: 'NORMAL',
      // datetime-local 是本地时间且无时区，后端要 Instant
      startAt: new Date('2026-10-08T10:00').toISOString(),
      endAt: null,
      minReadSeconds: null,
      requireScrollToEnd: false,
      requireConfirm: false,
    })
  })

  it('最短阅读秒数填写范围内整数时提交为数字', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[type="text"]').setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    await inputByType(wrapper, 'datetime-local').setValue('2026-10-08T10:00')
    // type="number" 上用 :value + @input，值必须仍是 string 才能被 trim/转换
    await inputByType(wrapper, 'number').setValue('10')

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ minReadSeconds: 10 })
  })

  it('最短阅读秒数超出 0–60 时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[type="text"]').setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    await inputByType(wrapper, 'datetime-local').setValue('2026-10-08T10:00')
    await inputByType(wrapper, 'number').setValue('61')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请输入 0–60 的整数')
  })

  it('生效结束早于或等于开始时间时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await wrapper.get('input[type="text"]').setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    const dateTimes = wrapper
      .findAll('input')
      .filter((element) => element.attributes('type') === 'datetime-local')
    await dateTimes[0].setValue('2026-10-08T10:00')
    await dateTimes[1].setValue('2026-10-08T09:00')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('结束时间必须晚于开始时间')
  })

  it('把 open 透传给 el-dialog，并响应它抛出的关闭事件', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(ElDialogStub)

    expect(dialog.props('modelValue')).toBe(true)

    dialog.vm.$emit('update:modelValue', false)
    await nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('标题或正文未填时预览按钮禁用，都填好后可用（阶段 D 前置预览）', async () => {
    const wrapper = mountDialog()
    const preview = buttonByText(wrapper, '预览学生视角')

    expect(preview.attributes('disabled')).toBeDefined()

    await wrapper.get('input[type="text"]').setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')

    expect(preview.attributes('disabled')).toBeUndefined()
  })
})
