<script setup lang="ts">
import { onMounted } from 'vue'

import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import { handleApiError } from '@/api/notifications'
import { useAppStore } from '@/stores/app'
import { useStudentContextStore } from '@/stores/studentContext'

const appStore = useAppStore()
const studentStore = useStudentContextStore()
onMounted(() => {
  appStore.checkBackend()
  studentStore.initialize().catch(handleApiError)
})
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-workspace">
      <AppHeader />
      <main class="app-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--color-surface-muted);
}

.app-workspace {
  min-width: 0;
}

.app-content {
  min-height: calc(100vh - var(--header-height));
  padding: 28px 32px;
}

@media (max-width: 1100px) {
  .app-content {
    padding: 24px;
  }
}
</style>
