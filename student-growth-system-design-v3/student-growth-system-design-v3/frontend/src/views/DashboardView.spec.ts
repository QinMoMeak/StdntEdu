import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { DashboardDto, Student } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { dashboardService } from '@/api/services/dashboardService'
import { createTestRouter } from '@/router'
import { useStudentContextStore } from '@/stores/studentContext'
import DashboardView from './DashboardView.vue'

vi.mock('@/api/services/dashboardService', () => ({ dashboardService: { get: vi.fn() } }))

const studentA: Student = {
  id: '9007199254740993',
  studentCode: 'STU0001',
  name: '小明',
  currentStageId: '1',
  currentGradeId: '2',
  version: 0,
}
const studentB: Student = { ...studentA, id: '9007199254740995', studentCode: 'STU0002', name: '小红' }

function dashboard(examName?: string): DashboardDto {
  return {
    today: {
      studyDurationSeconds: 3600,
      completedTaskCount: 2,
      totalTaskCount: 3,
      dueReviewCount: 4,
      overdueReviewCount: 1,
      waitingResourceCount: 2,
      learningResourceCount: 1,
    },
    latestExam: examName
      ? {
          id: '12',
          examName,
          examType: 'MONTHLY' as never,
          examDate: new Date('2026-08-20T00:00:00.000Z'),
          totalScore: 88,
          totalFullScore: 100,
          scoreRate: 0.88,
        }
      : undefined,
    scoreTrends: [],
    weakKnowledge: [],
    dueReviews: [],
    waitingResources: [],
    recentStudyLogs: [],
    aiSuggestions: [],
    statisticsPeriod: {
      startDate: new Date('2026-07-26T00:00:00.000Z'),
      endDate: new Date('2026-08-24T00:00:00.000Z'),
    },
  }
}

function emptyDashboard(): DashboardDto {
  const value = dashboard()
  value.today = {
    studyDurationSeconds: 0,
    completedTaskCount: 0,
    totalTaskCount: 0,
    dueReviewCount: 0,
    overdueReviewCount: 0,
    waitingResourceCount: 0,
    learningResourceCount: 0,
  }
  return value
}

async function mountDashboard(configure: (store: ReturnType<typeof useStudentContextStore>) => void): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useStudentContextStore()
  configure(store)
  const router = createTestRouter()
  await router.push('/dashboard')
  await router.isReady()
  return mount(DashboardView, { global: { plugins: [pinia, router, ElementPlus] } })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => (resolve = done))
  return { promise, resolve }
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dashboardService.get).mockResolvedValue(dashboard('月考'))
  })

  it('distinguishes no students from students with no explicit selection', async () => {
    const empty = await mountDashboard((store) => {
      store.initialized = true
      store.students = []
    })
    expect(empty.text()).toContain('尚未创建学生档案')
    empty.unmount()

    const unselected = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA]
    })
    expect(unselected.text()).toContain('请选择学生')
    expect(dashboardService.get).not.toHaveBeenCalled()
  })

  it('shows local loading without locking the application', async () => {
    vi.mocked(dashboardService.get).mockReturnValue(new Promise(() => undefined))
    const wrapper = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA]
      store.selectStudent(studentA)
    })
    expect(wrapper.find('.el-skeleton').exists()).toBe(true)
  })

  it('renders real Dashboard DTO fields and ratio percentages', async () => {
    const wrapper = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA]
      store.selectStudent(studentA)
    })
    await flushPromises()
    expect(dashboardService.get).toHaveBeenCalledWith(studentA.id)
    expect(wrapper.text()).toContain('小明的学习概览')
    expect(wrapper.text()).toContain('2 / 3')
    expect(wrapper.text()).toContain('月考')
    expect(wrapper.text()).toContain('88.0%')
  })

  it('shows the distinct empty-business-data state without invented values', async () => {
    vi.mocked(dashboardService.get).mockResolvedValue(emptyDashboard())
    const wrapper = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA]
      store.selectStudent(studentA)
    })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无成绩、错题等学习数据')
    expect(wrapper.text()).not.toContain('月考')
  })

  it('keeps a network error in-page and retries without clearing student context', async () => {
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.mocked(dashboardService.get)
      .mockRejectedValueOnce(new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: '无法连接本地后端服务' }))
      .mockResolvedValueOnce(dashboard('重试成功'))
    const wrapper = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA]
      store.selectStudent(studentA)
    })
    await flushPromises()
    expect(wrapper.text()).toContain('概览加载失败')
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBe(studentA.id)
    const retry = wrapper.findAll('button').find((item) => item.text().includes('重试'))
    if (!retry) throw new Error('Retry button not found')
    await retry.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('重试成功')
  })

  it('ignores a stale response after switching the current student', async () => {
    const requestA = deferred<DashboardDto>()
    const requestB = deferred<DashboardDto>()
    vi.mocked(dashboardService.get).mockImplementation((id) => (id === studentA.id ? requestA.promise : requestB.promise))
    const wrapper = await mountDashboard((store) => {
      store.initialized = true
      store.students = [studentA, studentB]
      store.selectStudent(studentA)
    })
    const store = useStudentContextStore()
    store.selectStudent(studentB)
    await flushPromises()
    requestB.resolve(dashboard('小红月考'))
    await flushPromises()
    requestA.resolve(dashboard('小明月考'))
    await flushPromises()
    expect(dashboardService.get).toHaveBeenNthCalledWith(1, studentA.id)
    expect(dashboardService.get).toHaveBeenNthCalledWith(2, studentB.id)
    expect(wrapper.text()).toContain('小红月考')
    expect(wrapper.text()).not.toContain('小明月考')
  })
})
