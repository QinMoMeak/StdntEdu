<script setup lang="ts">
import { Clock } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title)
</script>

<template>
  <section class="placeholder-view">
    <span class="placeholder-icon"><Clock /></span>
    <h2>{{ title }}</h2>
    <p>将在后续阶段实现</p>
  </section>
</template>

<style scoped>
.placeholder-view {
  display: grid;
  align-content: center;
  justify-items: center;
  min-height: calc(100vh - var(--header-height) - 56px);
  color: var(--color-text-muted);
  text-align: center;
}

.placeholder-icon {
  display: grid;
  width: 48px;
  height: 48px;
  margin-bottom: 16px;
  place-items: center;
  color: #326e5a;
  background: #dff3eb;
  border-radius: 6px;
}

.placeholder-icon svg {
  width: 24px;
}

h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
  font-weight: 650;
}

p {
  margin: 8px 0 0;
  font-size: 14px;
}
</style>
