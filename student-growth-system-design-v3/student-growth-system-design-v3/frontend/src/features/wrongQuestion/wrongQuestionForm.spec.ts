import { describe, expect, it } from 'vitest'

import type { Wrong } from '@/api/generated'
import { WrongSource, WrongStatus } from '@/api/generated'
import {
  buildWrongQuestionRequest,
  emptyWrongQuestionForm,
  newKnowledgeLink,
  validateWrongQuestionForm,
  wrongQuestionToForm,
} from './wrongQuestionForm'

const studentId = '9007199254740993'
const enabledQuestions = new Set(['SHORT_ANSWER', 'CALCULATION'])
const enabledErrors = new Set(['CARELESS'])

function validForm() {
  const form = emptyWrongQuestionForm()
  Object.assign(form, { subjectId: '7', questionText: '  题目  ', questionType: 'SHORT_ANSWER', errorType: 'CARELESS' })
  return form
}

describe('wrong question form mapping', () => {
  it('maps codes, string IDs and generated KnowledgeLink fields without labels', () => {
    const form = validForm()
    form.knowledgePoints = [{ ...newKnowledgeLink(), knowledgeId: '9007199254740994', primary: true, confidence: 0.85 }]
    expect(buildWrongQuestionRequest(form, studentId)).toEqual(expect.objectContaining({
      studentId, subjectId: '7', questionType: 'SHORT_ANSWER', errorType: 'CARELESS', questionText: '题目',
      knowledgePoints: [{ knowledgeId: '9007199254740994', primary: true, confidence: 0.85 }],
    }))
  })

  it('allows create to omit questionType and update to send explicit null', () => {
    const form = validForm()
    form.questionType = ''
    expect(buildWrongQuestionRequest(form, studentId).questionType).toBeUndefined()
    form.version = 4
    expect(buildWrongQuestionRequest(form, studentId)).toMatchObject({ questionType: null, version: 4 })
  })

  it('restores questionType, status, version and knowledge from detail for editing', () => {
    const wrong: Wrong = {
      id: '10', studentId, subjectId: '7', sourceType: WrongSource.Exam, questionType: 'CALCULATION',
      questionText: '题目', errorType: 'CARELESS', knowledgePoints: [{ knowledgeId: '11', primary: true, confidence: 0.7 }],
      status: WrongStatus.Reviewing, version: 6,
    }
    expect(wrongQuestionToForm(wrong)).toMatchObject({
      questionType: 'CALCULATION', errorType: 'CARELESS', status: WrongStatus.Reviewing, version: 6,
      knowledgePoints: [{ knowledgeId: '11', primary: true, confidence: 0.7 }],
    })
  })

  it('rejects disabled dictionary values but permits nullable categories', () => {
    const form = validForm()
    form.questionType = 'DISABLED'
    expect(validateWrongQuestionForm(form, enabledQuestions, enabledErrors)).toContain('可用的题型')
    form.questionType = ''
    form.errorType = ''
    expect(validateWrongQuestionForm(form, enabledQuestions, enabledErrors)).toBeNull()
  })

  it('rejects duplicate knowledge, multiple primary rows and confidence outside 0..1', () => {
    const form = validForm()
    form.knowledgePoints = [
      { ...newKnowledgeLink(), knowledgeId: '11', primary: true },
      { ...newKnowledgeLink(), knowledgeId: '11', primary: false },
    ]
    expect(validateWrongQuestionForm(form, enabledQuestions, enabledErrors)).toContain('重复')
    form.knowledgePoints[1]!.knowledgeId = '12'
    form.knowledgePoints[1]!.primary = true
    expect(validateWrongQuestionForm(form, enabledQuestions, enabledErrors)).toContain('主要')
    form.knowledgePoints[1]!.primary = false
    form.knowledgePoints[1]!.confidence = 1.1
    expect(validateWrongQuestionForm(form, enabledQuestions, enabledErrors)).toContain('0 到 1')
  })
})
