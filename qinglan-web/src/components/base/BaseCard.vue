<script setup lang="ts">
withDefaults(defineProps<{ padded?: boolean }>(), { padded: true })
</script>

<template>
  <section class="ql-card" :class="padded ? 'p-6 max-sm:p-5' : ''">
    <slot />
  </section>
</template>
