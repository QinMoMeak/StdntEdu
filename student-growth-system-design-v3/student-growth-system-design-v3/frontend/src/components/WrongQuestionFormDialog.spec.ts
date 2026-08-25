import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Wrong } from '@/api/generated'
import { WrongSource, WrongStatus } from '@/api/generated'
import { wrongQuestionService } from '@/api/services/wrongQuestionService'
import type { WrongQuestionFormModel } from '@/features/wrongQuestion/wrongQuestionForm'
import WrongQuestionFormDialog from './WrongQuestionFormDialog.vue'

vi.mock('@/api/services/wrongQuestionService', () => ({ wrongQuestionService: { listKnowledge: vi.fn() } }))
vi.mock('@/api/notifications', () => ({ handleApiError: vi.fn() }))

const subjects = [
  { id: '7', code: 'MATH', name: '数学', enabled: true },
  { id: '8', code: 'CHINESE', name: '语文', enabled: true },
]
const questionTypes = [
  { id: '1', dictType: 'question_type', code: 'SHORT_ANSWER', name: '简答题', enabled: true },
  { id: '2', dictType: 'question_type', code: 'ESSAY', name: '作文题', enabled: false },
]
const errorTypes = [{ id: '3', dictType: 'wrong_question_error_type', code: 'CARELESS', name: '粗心', enabled: true }]
const wrong: Wrong = {
  id: '9007199254740993', studentId: '9007199254740994', subjectId: '7', sourceType: WrongSource.Exam,
  questionType: 'SHORT_ANSWER', questionText: '题目', errorType: 'CARELESS', status: WrongStatus.New, version: 4,
}

function render(edit: Wrong | null = null) {
  return mount(WrongQuestionFormDialog, {
    props: {
      modelValue: true, wrong: edit, currentStudentId: wrong.studentId, subjects, questionTypes, errorTypes,
      saving: false, conflict: false,
    },
    global: {
      plugins: [ElementPlus],
      stubs: { ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>' } },
    },
  })
}

function exposed(wrapper: ReturnType<typeof render>) {
  return wrapper.vm as unknown as {
    form: WrongQuestionFormModel
    changeSubject: () => void
    setPrimary: (row: WrongQuestionFormModel['knowledgePoints'][number]) => void
    submit: () => void
  }
}

describe('WrongQuestionFormDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(wrongQuestionService.listKnowledge).mockResolvedValue([
      { id: '11', subjectId: '7', nodeCode: 'K1', name: '一次方程', nodeType: 'POINT', levelNo: 1, enabled: true, sortOrder: 1, version: 0, createdAt: new Date(), updatedAt: new Date(), children: [] },
    ])
  })

  it('loads current-subject knowledge and disables historical dictionary items', async () => {
    const wrapper = render(wrong)
    await flushPromises()
    expect(wrongQuestionService.listKnowledge).toHaveBeenCalledWith('7')
    const essay = wrapper.findAllComponents({ name: 'ElOption' }).find((item) => item.props('value') === 'ESSAY')
    expect(essay?.props('disabled')).toBe(true)
  })

  it('clears incompatible knowledge when subject changes', async () => {
    vi.spyOn(ElMessage, 'info').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = render(wrong)
    const vm = exposed(wrapper)
    vm.form.knowledgePoints = [{ key: 1, knowledgeId: '11', primary: true, confidence: 0.8 }]
    vm.form.subjectId = '8'
    vm.changeSubject()
    await flushPromises()
    expect(vm.form.knowledgePoints).toEqual([])
    expect(wrongQuestionService.listKnowledge).toHaveBeenCalledWith('8')
  })

  it('keeps at most one primary knowledge row', () => {
    const wrapper = render()
    const vm = exposed(wrapper)
    vm.form.knowledgePoints = [
      { key: 1, knowledgeId: '11', primary: true, confidence: null },
      { key: 2, knowledgeId: '12', primary: true, confidence: null },
    ]
    vm.setPrimary(vm.form.knowledgePoints[1]!)
    expect(vm.form.knowledgePoints.map((item) => item.primary)).toEqual([false, true])
  })

  it('submits dynamic codes and generated fields for create', () => {
    const wrapper = render()
    const vm = exposed(wrapper)
    Object.assign(vm.form, { subjectId: '7', questionText: '题目', questionType: 'SHORT_ANSWER', errorType: 'CARELESS' })
    vm.submit()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      studentId: wrong.studentId, subjectId: '7', questionType: 'SHORT_ANSWER', errorType: 'CARELESS',
    })
    expect(JSON.stringify(wrapper.emitted('submit')?.[0]?.[0])).not.toContain('简答题')
  })

  it('restores edit data, submits version and sends explicit null when questionType is cleared', () => {
    const wrapper = render(wrong)
    const vm = exposed(wrapper)
    expect(vm.form.questionType).toBe('SHORT_ANSWER')
    vm.form.questionType = ''
    vm.submit()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ version: 4, questionType: null })
  })

  it('keeps invalid input and exposes conflict reload', async () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = render()
    exposed(wrapper).form.questionText = '未完成题目'
    exposed(wrapper).submit()
    expect(wrapper.emitted('submit')).toBeUndefined()
    await wrapper.setProps({ conflict: true })
    expect(wrapper.text()).toContain('重新加载')
  })
})
