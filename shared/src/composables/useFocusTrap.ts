import { nextTick, onBeforeUnmount, watch, type Ref } from 'vue'

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * 弹层焦点陷阱：激活时把焦点收进容器，Tab / Shift + Tab 在容器内循环，失活时把焦点还给
 * 打开前的元素。无可聚焦元素（如强读弹窗倒计时未满、关闭按钮尚 disabled）时聚焦容器自身，
 * 保证焦点不留在背景页面上。
 *
 * <p>只做「焦点不逃逸」这一件事；ESC 的语义各弹层不同（普通弹层关闭、强读弹层忽略），
 * 仍由调用方自行处理，本组合不拦截。收口 ModalShell（举报 / 删除确认）与 AnnouncementModal
 * （强读）两处此前一强一弱的焦点处理。</p>
 *
 * <p>双端共用（学生端 ModalShell / AnnouncementModal、管理端弹层外壳都走它）。</p>
 *
 * @param root   弹层根元素（需带 tabindex="-1"，作为无内层可聚焦元素时的落点）
 * @param active 是否激活（弹层是否打开）；变化时自动启停
 */
export function useFocusTrap(root: Ref<HTMLElement | null>, active: () => boolean): void {
  let previouslyFocused: HTMLElement | null = null

  function focusable(): HTMLElement[] {
    const el = root.value
    return el ? Array.from(el.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)) : []
  }

  function focusFirst(): void {
    const [first] = focusable()
    if (first) {
      first.focus()
    } else {
      root.value?.focus()
    }
  }

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return
    }
    const items = focusable()
    const first = items[0]
    const last = items[items.length - 1]
    if (!first || !last) {
      event.preventDefault()
      root.value?.focus()
      return
    }
    const active = document.activeElement
    const inside = active ? (root.value?.contains(active) ?? false) : false
    if (event.shiftKey) {
      if (!inside || active === first) {
        event.preventDefault()
        last.focus()
      }
    } else if (!inside || active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  function activate(): void {
    previouslyFocused = (document.activeElement as HTMLElement | null) ?? null
    document.addEventListener('keydown', handleKeydown, true)
    void nextTick(focusFirst)
  }

  function deactivate(): void {
    document.removeEventListener('keydown', handleKeydown, true)
    previouslyFocused?.focus?.()
    previouslyFocused = null
  }

  watch(active, (isActive) => (isActive ? activate() : deactivate()), { immediate: true })

  onBeforeUnmount(deactivate)
}
