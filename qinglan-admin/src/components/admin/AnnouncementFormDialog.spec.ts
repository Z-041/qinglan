import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AnnouncementFormDialog from '@/components/admin/AnnouncementFormDialog.vue'

function mountDialog(): VueWrapper {
  return mount(AnnouncementFormDialog, { props: { open: true } })
}

function buttonByText(wrapper: VueWrapper, text: string): DOMWrapper<HTMLButtonElement> {
  const button = wrapper.findAll('button').find((element) => element.text() === text)
  if (!button) {
    throw new Error(`找不到按钮：${text}`)
  }
  return button as DOMWrapper<HTMLButtonElement>
}

/** 标题与最短阅读秒数是两个 type="text" 输入框，按出现顺序取 */
function textInput(wrapper: VueWrapper, index: number): DOMWrapper<HTMLInputElement> {
  return wrapper.findAll('input[type="text"]')[index] as DOMWrapper<HTMLInputElement>
}

function datePickers(wrapper: VueWrapper): DOMWrapper<HTMLInputElement>[] {
  return wrapper.findAll('input[type="datetime-local"]') as unknown as DOMWrapper<HTMLInputElement>[]
}

/** 填齐「标题 + 正文 + 生效开始」这三项必填 */
async function fillRequired(wrapper: VueWrapper): Promise<void> {
  await textInput(wrapper, 0).setValue('停水通知')
  await wrapper.get('textarea').setValue('今晚 22:00 起停水')
  await datePickers(wrapper)[0].setValue('2026-10-08T10:00')
}

describe('AnnouncementFormDialog', () => {
  it('标题、正文、生效开始任一为空时提交按钮保持禁用', async () => {
    const wrapper = mountDialog()
    const submit = buttonByText(wrapper, '提交草稿')

    expect(submit.attributes('disabled')).toBeDefined()

    await textInput(wrapper, 0).setValue('停水通知')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.get('textarea').setValue('今晚 22:00 起停水')
    expect(submit.attributes('disabled')).toBeDefined()

    await datePickers(wrapper)[0].setValue('2026-10-08T10:00')
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('提交时生效时间转 ISO-8601，最短阅读秒数留空传 null', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      title: '停水通知',
      content: '今晚 22:00 起停水',
      level: 'NORMAL',
      // datetime-local 给的是本地时间且无时区，后端要 Instant
      startAt: new Date('2026-10-08T10:00').toISOString(),
      endAt: null,
      minReadSeconds: null,
      requireScrollToEnd: false,
      requireConfirm: false,
    })
  })

  it('最短阅读秒数填写范围内整数时提交为数字', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await textInput(wrapper, 1).setValue('10')

    await buttonByText(wrapper, '提交草稿').trigger('click')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ minReadSeconds: 10 })
  })

  it('最短阅读秒数超出 0–60 时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await textInput(wrapper, 1).setValue('61')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请输入 0–60 的整数')
  })

  it('生效结束早于或等于开始时间时禁用提交并给出错误文案', async () => {
    const wrapper = mountDialog()
    await fillRequired(wrapper)
    await datePickers(wrapper)[1].setValue('2026-10-08T09:00')

    expect(buttonByText(wrapper, '提交草稿').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('结束时间必须晚于开始时间')
  })

  it('点取消抛出 close', async () => {
    const wrapper = mountDialog()

    await buttonByText(wrapper, '取消').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('标题或正文未填时预览按钮禁用，都填好后可用（阶段 D 前置预览）', async () => {
    const wrapper = mountDialog()
    const preview = buttonByText(wrapper, '预览学生视角')

    expect(preview.attributes('disabled')).toBeDefined()

    await textInput(wrapper, 0).setValue('停水通知')
    await wrapper.get('textarea').setValue('今晚 22:00 起停水')

    expect(preview.attributes('disabled')).toBeUndefined()
  })
})
