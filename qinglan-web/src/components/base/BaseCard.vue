<script setup lang="ts">
withDefaults(defineProps<{ padded?: boolean }>(), { padded: true })
</script>

<template>
  <section class="ql-card" :class="padded ? 'p-5 sm:p-6' : ''">
    <slot />
  </section>
</template>
