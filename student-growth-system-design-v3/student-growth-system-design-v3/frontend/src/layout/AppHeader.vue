<script setup lang="ts">
import { computed } from 'vue'
import { RefreshRight, User } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useStudentContextStore } from '@/stores/studentContext'

const route = useRoute()
const appStore = useAppStore()
const studentStore = useStudentContextStore()
const { backendStatus } = storeToRefs(appStore)
const { currentStudentId } = storeToRefs(studentStore)

const pageTitle = computed(() => route.meta.title)
const statusText = computed(() => {
  if (backendStatus.value === 'connected') return '后端已连接'
  if (backendStatus.value === 'checking') return '正在检查后端'
  if (backendStatus.value === 'unavailable') return '后端不可用'
  return '尚未检查后端'
})
</script>

<template>
  <header class="app-header">
    <div>
      <p class="header-context">学生个人教育成长档案系统</p>
      <h1>{{ pageTitle }}</h1>
    </div>
    <div class="header-actions">
      <div class="student-context">
        <el-icon><User /></el-icon>
        <span>
          <small>当前学生</small>
          <strong>{{ currentStudentId ? `ID ${currentStudentId}` : '未选择' }}</strong>
        </span>
      </div>
      <div class="backend-state" :data-status="backendStatus">
        <span class="status-dot" aria-hidden="true" />
        <span>{{ statusText }}</span>
      </div>
      <el-tooltip content="重新检查后端" placement="bottom">
        <el-button
          class="retry-button"
          :icon="RefreshRight"
          circle
          :loading="backendStatus === 'checking'"
          aria-label="重新检查后端"
          @click="appStore.checkBackend"
        />
      </el-tooltip>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--header-height);
  padding: 0 30px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-context {
  margin: 0 0 3px;
  color: var(--color-text-muted);
  font-size: 11px;
}

h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 20px;
  font-weight: 650;
}

.header-actions,
.student-context,
.backend-state {
  display: flex;
  align-items: center;
}

.header-actions {
  gap: 22px;
}

.student-context {
  gap: 9px;
  color: var(--color-text-muted);
}

.student-context > span {
  display: grid;
}

.student-context small {
  font-size: 11px;
}

.student-context strong {
  color: var(--color-text);
  font-size: 13px;
  font-weight: 600;
}

.backend-state {
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #9aa5b1;
  border-radius: 50%;
}

.backend-state[data-status='connected'] .status-dot {
  background: var(--color-success);
}

.backend-state[data-status='unavailable'] .status-dot {
  background: var(--color-danger);
}

.backend-state[data-status='checking'] .status-dot {
  background: var(--color-warning);
}

.retry-button {
  width: 32px;
  height: 32px;
}

@media (max-width: 1000px) {
  .header-context,
  .student-context small {
    display: none;
  }

  .header-actions {
    gap: 14px;
  }
}
</style>
