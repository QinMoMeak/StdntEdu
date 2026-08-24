import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ExamType } from '@/api/generated'
import type { ExamFormModel, SubjectScoreFormRow } from '@/features/score/examForm'
import { newKnowledgeRow } from '@/features/score/examForm'
import { scoreService } from '@/api/services/scoreService'
import ExamFormDialog from './ExamFormDialog.vue'

vi.mock('@/api/services/scoreService', () => ({ scoreService: { listKnowledge: vi.fn() } }))
vi.mock('@/api/notifications', () => ({ handleApiError: vi.fn() }))

function render() {
  return mount(ExamFormDialog, {
    props: {
      modelValue: true, exam: null, currentStudentId: '9007199254740993', terms: [],
      subjects: [
        { id: '7', code: 'MATH', name: '数学', enabled: true },
        { id: '8', code: 'CHINESE', name: '语文', enabled: true },
      ],
      examTypes: [{ value: ExamType.DailyTest, label: '日常测验' }], saving: false, conflict: false,
    },
    global: {
      plugins: [ElementPlus],
      stubs: { ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>' } },
    },
  })
}

function exposed(wrapper: ReturnType<typeof render>) {
  return wrapper.vm as unknown as {
    form: ExamFormModel
    changeSubject: (row: SubjectScoreFormRow) => void
    submit: () => void
  }
}

describe('ExamFormDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(scoreService.listKnowledge).mockResolvedValue([])
  })

  it('allows an empty knowledge tree without blocking the exam form', async () => {
    const wrapper = render()
    const vm = exposed(wrapper)
    vm.form.subjects[0]!.subjectId = '7'
    vm.changeSubject(vm.form.subjects[0]!)
    await flushPromises()
    expect(scoreService.listKnowledge).toHaveBeenCalledWith('7')
    expect(wrapper.text()).toContain('当前科目尚未维护知识点，可暂不填写知识点成绩')
  })

  it('clears incompatible knowledge rows when the subject changes', async () => {
    vi.spyOn(ElMessage, 'info').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = render()
    const vm = exposed(wrapper)
    vm.form.subjects[0]!.knowledgeScores = [{ ...newKnowledgeRow(), knowledgeId: '11' }]
    vm.form.subjects[0]!.subjectId = '8'
    vm.changeSubject(vm.form.subjects[0]!)
    await flushPromises()
    expect(vm.form.subjects[0]!.knowledgeScores).toEqual([])
    expect(ElMessage.info).toHaveBeenCalled()
  })

  it('submits the generated request shape with context student ID', async () => {
    const wrapper = render()
    const vm = exposed(wrapper)
    Object.assign(vm.form, { examName: '单元测验', examDate: '2026-05-10' })
    Object.assign(vm.form.subjects[0]!, { subjectId: '7', score: 90, fullScore: 100 })
    vm.submit()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ studentId: '9007199254740993', examName: '单元测验' })
  })

  it('keeps invalid user input in the open form', () => {
    vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({ close: vi.fn() }) as never)
    const wrapper = render()
    const vm = exposed(wrapper)
    vm.form.examName = '未完成考试'
    vm.submit()
    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(vm.form.examName).toBe('未完成考试')
  })
})
