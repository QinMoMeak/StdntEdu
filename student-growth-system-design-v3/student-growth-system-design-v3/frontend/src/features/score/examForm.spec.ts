import { describe, expect, it } from 'vitest'

import type { Exam, KnowledgeTreeNodeDto } from '@/api/generated'
import { ExamType } from '@/api/generated'
import {
  buildExamRequest, emptyExamForm, examToForm, flattenKnowledge, newKnowledgeRow, validateExamForm,
} from './examForm'

function validForm() {
  const form = emptyExamForm()
  form.examName = '期中考试'
  form.examDate = '2026-05-10'
  Object.assign(form.subjects[0]!, { subjectId: '7', score: 88, fullScore: 100 })
  return form
}

describe('exam form mapping and validation', () => {
  it('builds ExamCreate with studentId from context and no total fields', () => {
    const request = buildExamRequest(validForm(), '9007199254740995')
    expect(request.studentId).toBe('9007199254740995')
    expect(request.examDate.toISOString().substring(0, 10)).toBe('2026-05-10')
    expect(request).not.toHaveProperty('totalScore')
    expect(request).not.toHaveProperty('totalFullScore')
  })

  it('maps detail to edit form and submits optimistic-lock version', () => {
    const exam: Exam = {
      id: '9', studentId: '8', examName: '月考', examType: ExamType.MonthlyExam,
      examDate: new Date('2026-04-01T00:00:00Z'), subjects: [{ subjectId: '7', score: 80, fullScore: 100 }], version: 6,
    }
    expect(buildExamRequest(examToForm(exam), exam.studentId)).toMatchObject({ version: 6, studentId: '8' })
  })

  it('rejects missing and duplicate subjects', () => {
    const form = validForm()
    form.subjects.push({ ...form.subjects[0]!, key: 99, knowledgeScores: [] })
    expect(validateExamForm(form)).toContain('重复录入学科')
    form.subjects = []
    expect(validateExamForm(form)).toContain('至少录入一科')
  })

  it('validates score and fullScore bounds', () => {
    const form = validForm()
    form.subjects[0]!.score = 101
    expect(validateExamForm(form)).toContain('成绩不能超过满分')
  })

  it('requires class rank and size as a valid pair without treating zero as empty', () => {
    const form = validForm()
    form.subjects[0]!.classRank = 2
    expect(validateExamForm(form)).toContain('同时填写')
    form.subjects[0]!.classSize = 1
    expect(validateExamForm(form)).toContain('1 到人数之间')
    form.subjects[0]!.classRank = 0
    expect(validateExamForm(form)).toContain('1 到人数之间')
  })

  it('requires grade rank and size as a valid pair', () => {
    const form = validForm()
    form.subjects[0]!.gradeRank = 3
    form.subjects[0]!.gradeSize = 2
    expect(validateExamForm(form)).toContain('年级排名')
  })

  it('rejects duplicate knowledge IDs', () => {
    const form = validForm()
    const first = { ...newKnowledgeRow(), knowledgeId: '11', score: 8, fullScore: 10, questionCount: 10, correctCount: 8 }
    form.subjects[0]!.knowledgeScores = [first, { ...first, key: 100 }]
    expect(validateExamForm(form)).toContain('重复录入知识点')
  })

  it('validates knowledge score and correct-count bounds', () => {
    const form = validForm()
    form.subjects[0]!.knowledgeScores = [{ ...newKnowledgeRow(), knowledgeId: '11', score: 11, fullScore: 10, questionCount: 5, correctCount: 6 }]
    expect(validateExamForm(form)).toContain('知识点成绩')
  })

  it('maps knowledge rows to the generated nested DTO', () => {
    const form = validForm()
    form.subjects[0]!.knowledgeScores = [{ ...newKnowledgeRow(), knowledgeId: '11', score: 8, fullScore: 10, questionCount: 10, correctCount: 8 }]
    expect(buildExamRequest(form, '8').subjects[0]?.knowledgeScores?.[0]).toEqual({ knowledgeId: '11', score: 8, fullScore: 10, questionCount: 10, correctCount: 8 })
  })

  it('flattens enabled nodes only while preserving string IDs', () => {
    const node = (id: string, enabled: boolean, children: KnowledgeTreeNodeDto[] = []): KnowledgeTreeNodeDto => ({
      id, subjectId: '7', nodeCode: `K${id}`, name: `节点${id}`, nodeType: 'POINT', levelNo: 1, enabled, sortOrder: 0,
      version: 1, createdAt: new Date(), updatedAt: new Date(), children,
    })
    expect(flattenKnowledge([node('9007199254740993', true, [node('2', false), node('3', true)])]).map((item) => item.id))
      .toEqual(['9007199254740993', '3'])
  })
})
