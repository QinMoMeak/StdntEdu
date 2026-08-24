<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute } from 'vue-router'
import {
  Calendar,
  Collection,
  Cpu,
  DataAnalysis,
  Document,
  FolderOpened,
  House,
  MagicStick,
  Reading,
  Refresh,
  Setting,
  Tickets,
  TrendCharts,
  UploadFilled,
  Warning,
} from '@element-plus/icons-vue'

interface NavigationItem {
  label: string
  path: string
  icon: Component
}

interface NavigationGroup {
  label: string
  items: NavigationItem[]
}

const route = useRoute()
const groups: NavigationGroup[] = [
  { label: '', items: [{ label: '首页', path: '/dashboard', icon: House }] },
  {
    label: '学习数据',
    items: [
      { label: '成绩', path: '/scores', icon: DataAnalysis },
      { label: '错题', path: '/wrong-questions', icon: Warning },
      { label: '掌握度', path: '/mastery', icon: TrendCharts },
    ],
  },
  {
    label: '学习过程',
    items: [
      { label: '学习资源', path: '/resources', icon: Reading },
      { label: '学习日志', path: '/study-logs', icon: Document },
      { label: '学习计划', path: '/study-plans', icon: Calendar },
    ],
  },
  {
    label: '成长',
    items: [
      { label: '成长事件', path: '/growth-events', icon: Collection },
      { label: '成长报告', path: '/reports', icon: Tickets },
    ],
  },
  {
    label: 'AI',
    items: [
      { label: 'AI 识别', path: '/ai', icon: MagicStick },
      { label: 'AI 模型', path: '/ai/models', icon: Cpu },
    ],
  },
  {
    label: '数据',
    items: [
      { label: '导入导出', path: '/import-export', icon: UploadFilled },
      { label: '备份恢复', path: '/backup', icon: Refresh },
    ],
  },
  { label: '系统', items: [{ label: '设置', path: '/settings', icon: Setting }] },
]
</script>

<template>
  <aside class="sidebar" aria-label="主导航">
    <div class="brand">
      <span class="brand-mark"><FolderOpened /></span>
      <span class="brand-copy">
        <strong>学生成长档案</strong>
        <small>Local V1</small>
      </span>
    </div>
    <nav class="navigation">
      <section v-for="group in groups" :key="group.label || 'home'" class="nav-section">
        <p v-if="group.label" class="nav-heading">{{ group.label }}</p>
        <RouterLink
          v-for="item in group.items"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </section>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  color: #eef2f7;
  background: #18222d;
  border-right: 1px solid #111923;
}

.brand {
  display: flex;
  align-items: center;
  min-height: var(--header-height);
  padding: 0 20px;
  border-bottom: 1px solid #2b3642;
}

.brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  margin-right: 11px;
  place-items: center;
  color: #17212b;
  background: #64d2a3;
  border-radius: 6px;
}

.brand-mark svg {
  width: 19px;
}

.brand-copy {
  display: grid;
  min-width: 0;
}

.brand-copy strong {
  overflow: hidden;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy small {
  margin-top: 2px;
  color: #8fa0b1;
  font-size: 11px;
}

.navigation {
  padding: 14px 10px 24px;
}

.nav-section + .nav-section {
  margin-top: 15px;
}

.nav-heading {
  margin: 0 10px 6px;
  color: #8394a6;
  font-size: 11px;
  font-weight: 600;
}

.nav-item {
  display: flex;
  align-items: center;
  min-height: 38px;
  padding: 0 11px;
  color: #c8d1db;
  text-decoration: none;
  border-radius: 5px;
}

.nav-item:hover {
  color: #ffffff;
  background: #25313d;
}

.nav-item.active {
  color: #ffffff;
  background: #2d3c49;
  box-shadow: inset 3px 0 #64d2a3;
}

.nav-item .el-icon {
  width: 18px;
  margin-right: 10px;
  font-size: 17px;
}
</style>
