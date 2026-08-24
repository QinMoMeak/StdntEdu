import { createMemoryHistory, createRouter, createWebHistory, type RouterHistory, type RouteRecordRaw } from 'vue-router'

import AppShell from '@/layout/AppShell.vue'
import DashboardView from '@/views/DashboardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import ScoreView from '@/views/ScoreView.vue'
import StudentView from '@/views/StudentView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
  }
}

const placeholderRoutes: RouteRecordRaw[] = [
  { path: 'wrong-questions', component: PlaceholderView, meta: { title: '错题' } },
  { path: 'mastery', component: PlaceholderView, meta: { title: '掌握度' } },
  { path: 'resources', component: PlaceholderView, meta: { title: '学习资源' } },
  { path: 'study-logs', component: PlaceholderView, meta: { title: '学习日志' } },
  { path: 'study-plans', component: PlaceholderView, meta: { title: '学习计划' } },
  { path: 'growth-events', component: PlaceholderView, meta: { title: '成长事件' } },
  { path: 'reports', component: PlaceholderView, meta: { title: '成长报告' } },
  { path: 'ai', component: PlaceholderView, meta: { title: 'AI 识别' } },
  { path: 'ai/models', component: PlaceholderView, meta: { title: 'AI 模型' } },
  { path: 'import-export', component: PlaceholderView, meta: { title: '导入导出' } },
  { path: 'backup', component: PlaceholderView, meta: { title: '备份恢复' } },
  { path: 'settings', component: PlaceholderView, meta: { title: '设置' } },
]

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [
      {
        path: '/',
        component: AppShell,
        meta: { title: '首页' },
        children: [
          { path: '', redirect: '/dashboard' },
          { path: 'dashboard', component: DashboardView, meta: { title: '学习概览' } },
          { path: 'students', component: StudentView, meta: { title: '学生档案' } },
          { path: 'scores', component: ScoreView, meta: { title: '成绩与考试' } },
          ...placeholderRoutes,
        ],
      },
    ],
  })
}

export function createTestRouter() {
  return createAppRouter(createMemoryHistory())
}

export const router = createAppRouter()
