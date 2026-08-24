import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it } from 'vitest'

import type { Exam } from '@/api/generated'
import { ExamType } from '@/api/generated'
import ExamDetailDrawer from './ExamDetailDrawer.vue'

const exam: Exam = {
  id: '9007199254740993', studentId: '8', examName: '期中考试', examType: ExamType.Midterm,
  examDate: new Date('2026-05-10T00:00:00Z'), totalScore: 88, totalFullScore: 100, totalScoreRate: 0.88, version: 2,
  subjects: [{
    subjectId: '7', score: 88, fullScore: 100, classRank: null, classSize: null,
    knowledgeScores: [{ knowledgeId: '11', knowledgeCode: 'K1', knowledgeName: '分数运算', score: 8, fullScore: 10, scoreRate: 0.8, questionCount: 10, correctCount: 8, correctRate: 0.8 }],
  }],
}

function render(value: Exam) {
  return mount(ExamDetailDrawer, {
    props: { modelValue: true, exam: value, loading: false, subjects: [{ id: '7', code: 'MATH', name: '数学', enabled: true }], terms: [], examTypeLabel: () => '期中考试' },
    global: { plugins: [ElementPlus], stubs: { ElDrawer: { template: '<div><slot /></div>' } } },
  })
}

describe('ExamDetailDrawer', () => {
  it('renders backend totals, rates and null ranks without recalculation', () => {
    const wrapper = render(exam)
    expect(wrapper.text()).toContain('88 / 100')
    expect(wrapper.text()).toContain('88.0%')
    expect(wrapper.text()).toContain('班级 - / -')
  })

  it('renders subject and knowledge score details', () => {
    const wrapper = render(exam)
    expect(wrapper.text()).toContain('数学')
    expect(wrapper.findComponent({ name: 'ElTable' }).props('data')).toEqual(exam.subjects[0]?.knowledgeScores)
  })

  it('shows an explicit empty state when a subject has no knowledge scores', () => {
    const wrapper = render({ ...exam, subjects: [{ subjectId: '7', score: 88, fullScore: 100 }] })
    expect(wrapper.text()).toContain('暂无知识点成绩')
  })
})
