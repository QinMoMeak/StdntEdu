import { defineComponent } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ReviewCreate, Student, Wrong, WrongCreate, WrongUpdate } from '@/api/generated'
import { ReviewResult, WrongSource, WrongStatus } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { reviewService } from '@/api/services/reviewService'
import { wrongQuestionService } from '@/api/services/wrongQuestionService'
import { createTestRouter } from '@/router'
import { useStudentContextStore } from '@/stores/studentContext'
import WrongQuestionView from './WrongQuestionView.vue'

vi.mock('@/api/services/wrongQuestionService', () => ({
  wrongQuestionService: {
    list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), listSubjects: vi.fn(),
    listDictionary: vi.fn(), listKnowledge: vi.fn(),
  },
}))
vi.mock('@/api/services/reviewService', () => ({ reviewService: { listDue: vi.fn(), submit: vi.fn() } }))
vi.mock('@/api/notifications', () => ({ handleApiError: vi.fn() }))

const student: Student = { id: '9007199254740993', studentCode: 'S1', name: '小明', currentStageId: '1', currentGradeId: '2', version: 1 }
const otherStudent: Student = { ...student, id: '9007199254740994', studentCode: 'S2', name: '小红' }
const wrong: Wrong = {
  id: '9007199254740995', studentId: student.id, subjectId: '7', sourceType: WrongSource.Practice,
  questionType: 'SHORT_ANSWER', questionText: '一元一次方程怎么解？', correctAnswer: '移项求解', errorType: 'CARELESS',
  knowledgePoints: [{ knowledgeId: '11', primary: true, confidence: 0.9 }], status: WrongStatus.New, version: 3,
}

const FormStub = defineComponent({
  name: 'WrongQuestionFormDialog', props: ['modelValue', 'wrong', 'currentStudentId', 'conflict'],
  emits: ['update:modelValue', 'submit', 'reload'], template: '<div v-if="modelValue" data-test="wrong-form">错题表单</div>',
})
const DetailStub = defineComponent({
  name: 'WrongQuestionDetailDrawer', props: ['modelValue', 'wrong', 'loading', 'error', 'reviewEnabled'],
  emits: ['update:modelValue', 'edit', 'remove', 'retry', 'review'],
  setup(_, { expose }) { expose({ completeReview: vi.fn() }); return {} },
  template: '<div v-if="modelValue" data-test="wrong-detail">错题详情</div>',
})

function page(items: Wrong[] = [wrong], total = items.length) {
  return { page: 1, pageSize: 20, total, totalPages: total ? 1 : 0, items }
}

async function mountView(current: Student | null = student): Promise<VueWrapper> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useStudentContextStore()
  store.students = [student, otherStudent]
  store.initialized = true
  store.selectStudent(current)
  const router = createTestRouter()
  await router.push('/wrong-questions')
  await router.isReady()
  return mount(WrongQuestionView, {
    global: { plugins: [pinia, router, ElementPlus], stubs: { WrongQuestionFormDialog: FormStub, WrongQuestionDetailDrawer: DetailStub } },
  })
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!found) throw new Error(`Button not found: ${text}`)
  return found
}

describe('WrongQuestionView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(wrongQuestionService.list).mockResolvedValue(page())
    vi.mocked(reviewService.listDue).mockResolvedValue(page())
    vi.mocked(wrongQuestionService.get).mockResolvedValue(wrong)
    vi.mocked(wrongQuestionService.create).mockResolvedValue(wrong)
    vi.mocked(wrongQuestionService.update).mockResolvedValue(wrong)
    vi.mocked(wrongQuestionService.remove).mockResolvedValue()
    vi.mocked(reviewService.submit).mockResolvedValue({ reviewId: '90' })
    vi.mocked(wrongQuestionService.listSubjects).mockResolvedValue([{ id: '7', code: 'MATH', name: '数学', enabled: true }])
    vi.mocked(wrongQuestionService.listDictionary).mockImplementation(async (type) => type === 'question_type'
      ? [{ id: '1', dictType: type, code: 'SHORT_ANSWER', name: '简答题', enabled: true }]
      : [{ id: '2', dictType: type, code: 'CARELESS', name: '粗心', enabled: true }])
    vi.mocked(wrongQuestionService.listKnowledge).mockResolvedValue([])
  })

  it('does not request WrongQuestion or Due without a current student', async () => {
    const wrapper = await mountView(null)
    await flushPromises()
    expect(wrapper.text()).toContain('请先选择学生')
    expect(wrongQuestionService.list).not.toHaveBeenCalled()
    expect(reviewService.listDue).not.toHaveBeenCalled()
  })

  it('loads real server-paginated list, due data and dynamic labels for the current string ID', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrongQuestionService.list).toHaveBeenCalledWith(student.id, 1, 20)
    expect(reviewService.listDue).toHaveBeenCalledWith(student.id, 1, 10)
    expect(wrongQuestionService.listDictionary).toHaveBeenCalledWith('question_type')
    expect(wrongQuestionService.listDictionary).toHaveBeenCalledWith('wrong_question_error_type')
    expect(wrapper.text()).toContain('一元一次方程怎么解？')
    expect(wrapper.text()).toContain('简答题')
    expect(wrapper.text()).toContain('粗心')
  })

  it('uses table-local loading and distinct list and due empty states', async () => {
    vi.mocked(wrongQuestionService.list).mockReturnValueOnce(new Promise(() => undefined))
    vi.mocked(reviewService.listDue).mockResolvedValue(page([]))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.findAll('.el-loading-mask').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('当前没有到期复习内容')
  })

  it('keeps list and due errors local and retries independently', async () => {
    vi.mocked(wrongQuestionService.list).mockRejectedValueOnce(new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: '列表失败' }))
    vi.mocked(reviewService.listDue).mockRejectedValueOnce(new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Due失败' }))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('列表失败')
    expect(wrapper.text()).toContain('Due失败')
    vi.mocked(wrongQuestionService.list).mockResolvedValue(page())
    vi.mocked(reviewService.listDue).mockResolvedValue(page())
    for (const retry of wrapper.findAll('button').filter((item) => item.text().includes('重试'))) await retry.trigger('click')
    await flushPromises()
    expect(wrongQuestionService.list).toHaveBeenCalledTimes(2)
    expect(reviewService.listDue).toHaveBeenCalledTimes(2)
  })

  it('reloads both collections on student switch and ignores stale responses', async () => {
    let resolveList!: (value: ReturnType<typeof page>) => void
    let resolveDue!: (value: ReturnType<typeof page>) => void
    vi.mocked(wrongQuestionService.list)
      .mockReturnValueOnce(new Promise((resolve) => { resolveList = resolve }))
      .mockResolvedValueOnce(page([{ ...wrong, studentId: otherStudent.id, questionText: '新学生错题' }]))
    vi.mocked(reviewService.listDue)
      .mockReturnValueOnce(new Promise((resolve) => { resolveDue = resolve }))
      .mockResolvedValueOnce(page([{ ...wrong, studentId: otherStudent.id, questionText: '新学生待复习' }]))
    const wrapper = await mountView()
    await flushPromises()
    useStudentContextStore().selectStudent(otherStudent)
    await flushPromises()
    resolveList(page([{ ...wrong, questionText: '旧学生错题' }]))
    resolveDue(page([{ ...wrong, questionText: '旧学生待复习' }]))
    await flushPromises()
    expect(wrapper.text()).toContain('新学生错题')
    expect(wrapper.text()).not.toContain('旧学生错题')
  })

  it('creates with current student context and refreshes list and due', async () => {
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '新增错题').trigger('click')
    expect(wrapper.findComponent(FormStub).props('currentStudentId')).toBe(student.id)
    const request: WrongCreate = { studentId: student.id, subjectId: '7', sourceType: WrongSource.Practice, questionText: '题目' }
    wrapper.findComponent(FormStub).vm.$emit('submit', request)
    await flushPromises()
    expect(wrongQuestionService.create).toHaveBeenCalledWith(request)
    expect(wrongQuestionService.list).toHaveBeenCalledTimes(2)
    expect(reviewService.listDue).toHaveBeenCalledTimes(2)
  })

  it('keeps create open on failure', async () => {
    vi.mocked(wrongQuestionService.create).mockRejectedValue(new AppApiError({ status: 422, errorCode: 'VALIDATION_ERROR', message: '校验失败' }))
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '新增错题').trigger('click')
    wrapper.findComponent(FormStub).vm.$emit('submit', { ...wrong } satisfies WrongCreate)
    await flushPromises()
    expect(wrapper.find('[data-test="wrong-form"]').exists()).toBe(true)
  })

  it('loads detail before edit and submits the latest version', async () => {
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    expect(wrongQuestionService.get).toHaveBeenCalledWith(wrong.id)
    wrapper.findComponent(DetailStub).vm.$emit('edit')
    const request: WrongUpdate = { ...wrong, version: 3 }
    wrapper.findComponent(FormStub).vm.$emit('submit', request)
    await flushPromises()
    expect(wrongQuestionService.update).toHaveBeenCalledWith(wrong.id, expect.objectContaining({ version: 3 }))
  })

  it('keeps edit on version conflict and reloads latest data explicitly', async () => {
    vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: vi.fn() }) as never)
    vi.mocked(wrongQuestionService.update).mockRejectedValue(new AppApiError({ status: 409, errorCode: 'DATA_VERSION_CONFLICT', message: '冲突' }))
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    wrapper.findComponent(DetailStub).vm.$emit('edit')
    wrapper.findComponent(FormStub).vm.$emit('submit', { ...wrong } satisfies WrongUpdate)
    await flushPromises()
    expect(wrapper.findComponent(FormStub).props('conflict')).toBe(true)
    vi.mocked(wrongQuestionService.get).mockResolvedValueOnce({ ...wrong, version: 4 })
    wrapper.findComponent(FormStub).vm.$emit('reload')
    await flushPromises()
    expect(wrapper.findComponent(FormStub).props('wrong').version).toBe(4)
  })

  it('confirms real delete and refreshes both collections', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '详情').trigger('click')
    await flushPromises()
    wrapper.findComponent(DetailStub).vm.$emit('remove')
    await flushPromises()
    expect(wrongQuestionService.remove).toHaveBeenCalledWith(wrong.id)
    expect(reviewService.listDue).toHaveBeenCalledTimes(2)
  })

  it('opens review only from Due and refreshes detail, Due and list after success', async () => {
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = await mountView()
    await flushPromises()
    await button(wrapper, '开始复习').trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(DetailStub).props('reviewEnabled')).toBe(true)
    const request: ReviewCreate = { reviewTime: new Date(), result: ReviewResult.Correct }
    wrapper.findComponent(DetailStub).vm.$emit('review', request)
    await flushPromises()
    expect(reviewService.submit).toHaveBeenCalledWith(wrong.id, request)
    expect(wrongQuestionService.get).toHaveBeenCalledTimes(2)
    expect(wrongQuestionService.list).toHaveBeenCalledTimes(2)
    expect(reviewService.listDue).toHaveBeenCalledTimes(2)
  })

  it('has no fake filters, attachment upload, review history or local mastery controls', async () => {
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).not.toContain('筛选')
    expect(wrapper.text()).not.toContain('上传')
    expect(wrapper.text()).not.toContain('复习历史')
    expect(wrapper.text()).not.toContain('掌握度调整')
  })
})
