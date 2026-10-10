<script setup lang="ts">
/**
 * 学生端右栏两列布局：主列（弹性）+ 固定 320px 侧栏。
 *
 * 四处页面（首页 / 详情 / 发布 / 我的）的这套网格与侧栏外壳逐字相同，收敛到一处：
 * 若某一页的断点或 sticky 写歪，侧栏会在窄屏（<lg）挤掉正文——集中后不会再各自漂移。
 * 顶部间距各页节奏不同（mt-3 / mt-4 / mt-5），由调用方经 class 传入。
 *
 * 侧栏内容放 #aside 插槽。**本组件持有唯一的 <aside> 元素**，插槽内不要再套 <aside>，
 * 否则形成嵌套 aside 且页面里「aside 恰好一个」的断言会失效。
 */
</script>

<template>
  <div class="grid grid-cols-[minmax(0,1fr)_320px] gap-10 max-lg:(grid-cols-1 gap-6)">
    <div class="min-w-0 self-start">
      <slot />
    </div>
    <aside class="sticky top-24 flex flex-col gap-4 self-start max-lg:hidden">
      <slot name="aside" />
    </aside>
  </div>
</template>
