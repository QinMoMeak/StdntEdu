import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Exam, ExamCreate, ExamUpdate, ScoreListItemDto, Student } from '@/api/generated'
import { ExamType } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { scoreService } from '@/api/services/scoreService'
import { createTestRouter } from '@/router'
import { useStudentContextStore } from '@/stores/studentContext'
import ScoreView from './ScoreView.vue'

vi.mock('@/api/services/scoreService', () => ({
  scoreService: {
    list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
    listTerms: vi.fn(), listSubjects: vi.fn(), listKnowledge: vi.fn(),
  },
}))
vi.mock('@/api/notifications', () => ({ handleApiError: vi.fn() }))

const student: Student = { id: '9007199254740993', studentCode: 'S1', name: '小明', currentStageId: '1', currentGradeId: '2', version: 1 }
const otherStudent: Student = { ...student, id: '9007199254740994', studentCode: 'S2', name: '小红' }
const score: ScoreListItemDto = {
  id: '9007199254740995', examId: '9007199254740996', examName: '期中考试', examType: ExamType.Midterm,
  examDate: new Date('2026-05-10T00:00:00Z'), subjectId: '7', subjectName: '数学', score: 88, fullScore: 100,
  scoreRate: 0.88, classRank: null, gradeRank: 12,
}
const exam: Exam = {
  id: score.examId, studentId: student.id, examName: score.examName, examType: score.examType, examDate: score.examDate,
  subjects: [{ subjectId: '7', score: 88, fullScore: 100, knowledgeScores: [] }], totalScore: 88, totalFullScore: 100,
  totalScoreRate: 0.88, version: 4,
}

const FormStub = defineComponent({
  name: 'ExamFormDialog', props: ['modelValue', 'exam', 'currentStudentId', 'terms', 'subjects', 'saving', 'conflict'],
  emits: ['update:modelValue', 'submit', 'reload'], template: '<div v-if="modelValue" data-test="exam-form">考试表单</div>',
})
const DetailStub = defineComponent({
  name: 'ExamDetailDrawer', props: ['modelValue', 'exam', 'loading'], emits: ['update:modelValue', 'edit', 'remove'],
  template: '<div v-if="modelValue" data-test="exam-detail">考试详情</div>',
})

function page(items: ScoreListItemDto[] = [score]) {
  return { page: 1, pageSize: 20, total: items.length, totalPages: items.length ? 1 : 0, items }
}

async function mountView(current: Student | null = student): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useStudentContextStore()
  store.students = [student, otherStudent]
  store.initialized = true
  store.selectStudent(current)
  const router = createTestRouter()
  await router.push('/scores')
  await router.isReady()
  return mount(ScoreView, {
    global: { plugins: [pinia, router, ElementPlus], stubs: { ExamFormDialog: FormStub, ExamDetailDrawer: DetailStub } },
  })
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`Button not found: ${text}`)
  return found
}

describe('ScoreView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(scoreService.list).mockResolvedValue(page())
    vi.mocked(scoreService.get).mockResolvedValue(exam)
    vi.mocked(scoreService.create).mockResolvedValue(exam)
    vi.mocked(scoreService.update).mockResolvedValue(exam)
    vi.mocked(scoreService.remove).mockResolvedValue()
    vi.mocked(scoreService.listTerms).mockResolvedValue([])
    vi.mocked(scoreService.listSubjects).mockResolvedValue([{ id: '7', code: 'MATH', name: '数学', enabled: true }])
  })

  it('does not request scores without a current student', async () => {
    const wrapper = await mountView(null)
    await flushPromises()
    expect(wrapper.text()).toContain('请先选择学生')
    expect(scoreService.list).not.toHaveBeenCalled()
  })

  it('loads the server-paginated list for the current string student ID', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(scoreService.list).toHaveBeenCalledWith(student.id, expect.objectContaining({ page: 1, pageSize: 20 }))
    expect(wrapper.text()).toContain('期中考试')
    expect(wrapper.text()).toContain('88.0%')
    expect(wrapper.text()).toContain('数学')
  })

  it('shows table-local loading', async () => {
    vi.mocked(scoreService.list).mockReturnValue(new Promise(() => undefined))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.el-loading-mask').exists()).toBe(true)
  })

  it('distinguishes business empty and filtered empty states', async () => {
    vi.mocked(scoreService.list).mockResolvedValue(page([]))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('尚未记录考试成绩')
    ;(wrapper.vm as unknown as { filters: { keyword: string }; search: () => void }).filters.keyword = '不存在'
    ;(wrapper.vm as unknown as { search: () => void }).search()
    await flushPromises()
    expect(wrapper.text()).toContain('当前条件下无成绩')
  })

  it('keeps API errors visible and retries', async () => {
    vi.mocked(scoreService.list).mockRejectedValueOnce(new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: '连接失败' }))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('连接失败')
    vi.mocked(scoreService.list).mockResolvedValue(page())
    await button(wrapper, '重试').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('期中考试')
  })

  it('reloads on student switch and ignores the stale response', async () => {
    let resolveFirst!: (value: ReturnType<typeof page>) => void
    const first = new Promise<ReturnType<typeof page>>((resolve) => { resolveFirst = resolve })
    vi.mocked(scoreService.list).mockReturnValueOnce(first).mockResolvedValueOnce(page([{ ...score, examName: '新学生考试' }]))
    const wrapper = await mountView()
    await flushPromises()
    useStudentContextStore().selectStudent(otherStudent)
    await flushPromises()
    resolveFirst(page([{ ...score, examName: '旧学生考试' }]))
    await flushPromises()
    expect(wrapper.text()).toContain('新学生考试')
    expect(wrapper.text()).not.toContain('旧学生考试')
  })

  it('opens create with current student context and refreshes after success', async () => {
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '新增考试').trigger('click')
    const form = wrapper.findComponent(FormStub)
    expect(form.props('currentStudentId')).toBe(student.id)
    const body: ExamCreate = { studentId: student.id, examName: '期中考试', examType: ExamType.Midterm, examDate: score.examDate, subjects: exam.subjects }
    form.vm.$emit('submit', body)
    await flushPromises()
    expect(scoreService.create).toHaveBeenCalledWith(body)
    expect(scoreService.list).toHaveBeenCalledTimes(2)
  })

  it('keeps the create form open when the API rejects the request', async () => {
    vi.mocked(scoreService.create).mockRejectedValue(new AppApiError({ status: 422, errorCode: 'VALIDATION_ERROR', message: '校验失败' }))
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '新增考试').trigger('click')
    wrapper.findComponent(FormStub).vm.$emit('submit', {
      studentId: student.id, examName: '考试', examType: ExamType.Midterm, examDate: score.examDate, subjects: exam.subjects,
    } satisfies ExamCreate)
    await flushPromises()
    expect(wrapper.find('[data-test="exam-form"]').exists()).toBe(true)
  })

  it('loads detail, initializes edit and submits version', async () => {
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    expect(scoreService.get).toHaveBeenCalledWith(score.examId)
    wrapper.findComponent(DetailStub).vm.$emit('edit')
    await flushPromises()
    expect(wrapper.findComponent(FormStub).props('exam')).toEqual(exam)
    const body: ExamUpdate = { studentId: student.id, examName: exam.examName, examType: exam.examType, examDate: exam.examDate, subjects: exam.subjects, version: 4 }
    wrapper.findComponent(FormStub).vm.$emit('submit', body)
    await flushPromises()
    expect(scoreService.update).toHaveBeenCalledWith(exam.id, expect.objectContaining({ version: 4 }))
  })

  it('keeps edit open on conflict and reloads without overwriting automatically', async () => {
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.mocked(scoreService.update).mockRejectedValue(new AppApiError({ status: 409, errorCode: 'DATA_VERSION_CONFLICT', message: 'conflict' }))
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    wrapper.findComponent(DetailStub).vm.$emit('edit')
    wrapper.findComponent(FormStub).vm.$emit('submit', { ...exam, version: 4 } satisfies ExamUpdate)
    await flushPromises()
    expect(wrapper.findComponent(FormStub).props('conflict')).toBe(true)
    expect(wrapper.find('[data-test="exam-form"]').exists()).toBe(true)
    vi.mocked(scoreService.get).mockResolvedValueOnce({ ...exam, version: 5 })
    wrapper.findComponent(FormStub).vm.$emit('reload')
    await flushPromises()
    expect(scoreService.get).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent(FormStub).props('exam').version).toBe(5)
  })

  it('uses the frozen deleteExam operation and refreshes the list', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    wrapper.findComponent(DetailStub).vm.$emit('remove')
    await flushPromises()
    expect(scoreService.remove).toHaveBeenCalledWith(exam.id)
    expect(scoreService.list).toHaveBeenCalledTimes(2)
  })
})
