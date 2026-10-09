import { describe, expect, it } from 'vitest'
import { ref } from 'vue'

import { useRowKeyboardNav } from '@/composables/useRowKeyboardNav'

function keydown(key: string): KeyboardEvent {
  return new KeyboardEvent('keydown', { key })
}

describe('useRowKeyboardNav', () => {
  it('未获得焦点时 ↑/↓ 一概不响应，焦点行保持不变', () => {
    const rows = ref([{ id: 1 }, { id: 2 }])
    const nav = useRowKeyboardNav(
      () => rows.value,
      (row) => String(row.id),
    )

    expect(nav.activeIndex.value).toBe(-1)
    expect(nav.handleArrowKeys(keydown('ArrowDown'))).toBe(false)
    expect(nav.activeIndex.value).toBe(-1)
  })

  it('获得焦点后 ↑/↓ 移动焦点行，并夹在首末行边界内', () => {
    const rows = ref([{ id: 1 }, { id: 2 }, { id: 3 }])
    const nav = useRowKeyboardNav(
      () => rows.value,
      (row) => String(row.id),
    )
    nav.focus()

    expect(nav.handleArrowKeys(keydown('ArrowDown'))).toBe(true)
    expect(nav.activeIndex.value).toBe(0)

    // 一路向下：越过末行后停在末行
    nav.handleArrowKeys(keydown('ArrowDown'))
    nav.handleArrowKeys(keydown('ArrowDown'))
    nav.handleArrowKeys(keydown('ArrowDown'))
    expect(nav.activeIndex.value).toBe(2)

    // 一路向上：越过首行后停在首行
    nav.handleArrowKeys(keydown('ArrowUp'))
    nav.handleArrowKeys(keydown('ArrowUp'))
    nav.handleArrowKeys(keydown('ArrowUp'))
    expect(nav.activeIndex.value).toBe(0)
  })

  it('返回当前选中项与其行键', () => {
    const rows = ref([{ id: 7 }, { id: 8 }])
    const nav = useRowKeyboardNav(
      () => rows.value,
      (row) => String(row.id),
    )

    expect(nav.activeItem.value).toBeNull()
    expect(nav.activeKey.value).toBeNull()

    nav.focus()
    nav.handleArrowKeys(keydown('ArrowDown'))

    expect(nav.activeItem.value).toEqual({ id: 7 })
    expect(nav.activeKey.value).toBe('7')
  })

  it('空列表时焦点恒为 -1', () => {
    const rows = ref<{ id: number }[]>([])
    const nav = useRowKeyboardNav(
      () => rows.value,
      (row) => String(row.id),
    )
    nav.focus()

    nav.handleArrowKeys(keydown('ArrowDown'))
    expect(nav.activeIndex.value).toBe(-1)
    expect(nav.activeItem.value).toBeNull()
  })

  it('列表刷新变短后越界的焦点由 clamp 夹回末行', () => {
    const rows = ref([{ id: 1 }, { id: 2 }, { id: 3 }])
    const nav = useRowKeyboardNav(
      () => rows.value,
      (row) => String(row.id),
    )
    nav.focus()
    nav.setActive(2)

    rows.value = [{ id: 1 }]
    nav.clamp()

    expect(nav.activeIndex.value).toBe(0)
    expect(nav.activeKey.value).toBe('1')
  })

  it('焦点在列表内部换元素时保持焦点态，真正移出列表才解除', () => {
    const nav = useRowKeyboardNav(
      () => [],
      (row: { id: number }) => String(row.id),
    )
    nav.focus()

    const container = document.createElement('div')
    const child = document.createElement('button')
    container.appendChild(child)

    nav.handleFocusOut({ relatedTarget: child, currentTarget: container } as unknown as FocusEvent)
    expect(nav.focused.value).toBe(true)

    nav.handleFocusOut({ relatedTarget: document.body, currentTarget: container } as unknown as FocusEvent)
    expect(nav.focused.value).toBe(false)
  })
})
