import { mount } from '@vue/test-utils'
import ElementPlus, { ElSelect } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AppHeader from './AppHeader.vue'
import AppSidebar from './AppSidebar.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import { createTestRouter } from '@/router'
import { useAppStore } from '@/stores/app'
import { useStudentContextStore } from '@/stores/studentContext'

describe('application shell', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders the grouped sidebar navigation', async () => {
    const router = createTestRouter()
    await router.push('/dashboard')
    await router.isReady()
    const wrapper = mount(AppSidebar, { global: { plugins: [router, ElementPlus] } })
    expect(wrapper.text()).toContain('学习数据')
    expect(wrapper.find('a[href="/scores"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/students"]').exists()).toBe(true)
  })

  it('navigates from the sidebar', async () => {
    const router = createTestRouter()
    await router.push('/dashboard')
    await router.isReady()
    const wrapper = mount(AppSidebar, { global: { plugins: [router, ElementPlus] } })
    await wrapper.get('a[href="/resources"]').trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/resources'))
  })

  it('shows page, student and backend status in the header', async () => {
    const router = createTestRouter()
    await router.push('/scores')
    await router.isReady()
    const appStore = useAppStore()
    const studentStore = useStudentContextStore()
    appStore.backendStatus = 'connected'
    studentStore.students = [
      { id: '1', studentCode: 'STU0001', name: '小明', currentStageId: '1', currentGradeId: '1', version: 0 },
    ]
    const wrapper = mount(AppHeader, { global: { plugins: [router, ElementPlus] } })
    expect(wrapper.text()).toContain('成绩')
    expect(wrapper.text()).toContain('当前学生')
    expect(wrapper.text()).toContain('后端已连接')
    expect(wrapper.text()).not.toMatch(/登录|退出登录/)
    wrapper.findComponent(ElSelect).vm.$emit('change', '1')
    expect(studentStore.currentStudentId).toBe('1')
  })

  it('renders the uniform placeholder copy', async () => {
    const router = createTestRouter()
    await router.push('/reports')
    await router.isReady()
    const wrapper = mount(PlaceholderView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('成长报告')
    expect(wrapper.text()).toContain('将在后续阶段实现')
  })
})
