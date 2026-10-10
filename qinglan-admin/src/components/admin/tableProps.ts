/**
 * 各管理端表格共有的 props 形状（数据 + 三态）。
 *
 * 5 张表此前各自逐字重复 `items / loading / failed`，抽成类型供
 * `defineProps<TableStateProps<T> & {…}>()` 复用，一眼即可对齐；列特有 props 仍由各表交叉补充。
 */
export interface TableStateProps<T> {
  items: T[]
  loading?: boolean
  failed?: boolean
}
