import { computed, ref } from 'vue'

/**
 * 列表行焦点（键盘流第一段，方案「审核工作台改造（阶段 A）」）。
 *
 * <p>只负责「当前聚焦哪一行」这一件事：`items` 变化时不自行迁移焦点，
 * 由调用方在处置成功后重新 `selectActive`——否则一条新内容插入头部就会把焦点挤走。</p>
 *
 * <p><b>安全边界</b>：所有移动与按键响应都以 {@link focused} 为前提。
 * 调用方把 `focus` / `handleFocusOut` / `handleArrowKeys` 挂在列表容器上，
 * 于是弹窗（Teleport 到 body）与搜索框里的 ↑/↓ 不会冒泡到这里，也就抢不走。</p>
 */
export function useRowKeyboardNav<T>(items: () => readonly T[], keyOf: (item: T) => string) {
  /** -1 表示尚无焦点行 */
  const activeIndex = ref(-1)
  /** 列表（或其内部元素）是否持有焦点；快捷键只在为真时生效 */
  const focused = ref(false)

  const activeItem = computed<T | null>(() => items()[activeIndex.value] ?? null)
  const activeKey = computed<string | null>(() => {
    const item = activeItem.value
    return item === null ? null : keyOf(item)
  })

  function focus(): void {
    focused.value = true
  }

  function blur(): void {
    focused.value = false
  }

  /**
   * 焦点移出列表时解除焦点态。焦点只在列表内部换元素（例如点到行内按钮）不算离开，
   * 否则点一下「处置」按钮就会把快捷键关掉。
   */
  function handleFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null
    const current = event.currentTarget as Node | null
    if (next && current && current.contains(next)) {
      return
    }
    focused.value = false
  }

  /** 点选某一行（点击行内按钮）时把焦点定到它，并夹在列表范围内 */
  function setActive(index: number): void {
    const count = items().length
    activeIndex.value = count === 0 ? -1 : Math.min(Math.max(index, 0), count - 1)
  }

  /** 列表被刷新（条目增减）后把越界的焦点夹回最后一行 */
  function clamp(): void {
    const count = items().length
    if (count === 0) {
      activeIndex.value = -1
      return
    }
    if (activeIndex.value > count - 1) {
      activeIndex.value = count - 1
    }
  }

  function move(delta: number): void {
    const count = items().length
    if (count === 0) {
      activeIndex.value = -1
      return
    }
    if (activeIndex.value < 0) {
      activeIndex.value = delta > 0 ? 0 : count - 1
      return
    }
    activeIndex.value = Math.min(Math.max(activeIndex.value + delta, 0), count - 1)
  }

  function moveUp(): void {
    move(-1)
  }

  function moveDown(): void {
    move(1)
  }

  function reset(): void {
    activeIndex.value = -1
    focused.value = false
  }

  /** ↑/↓ 移动行焦点；未获得焦点时不响应。返回是否已消费该按键 */
  function handleArrowKeys(event: KeyboardEvent): boolean {
    if (!focused.value) {
      return false
    }
    if (event.key === 'ArrowDown') {
      moveDown()
      return true
    }
    if (event.key === 'ArrowUp') {
      moveUp()
      return true
    }
    return false
  }

  return {
    activeIndex,
    activeItem,
    activeKey,
    focused,
    focus,
    blur,
    handleFocusOut,
    setActive,
    clamp,
    moveUp,
    moveDown,
    reset,
    handleArrowKeys,
  }
}
