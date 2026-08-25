import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ReviewCreate, Wrong, WrongCreate, WrongUpdate } from '@/api/generated'
import { ReviewResult, WrongSource, WrongStatus } from '@/api/generated'
import { api } from '@/api/client'
import { reviewService } from './reviewService'
import { wrongQuestionService } from './wrongQuestionService'

vi.mock('@/api/client', () => ({
  api: {
    listWrongQuestions: vi.fn(), getWrongQuestion: vi.fn(), createWrongQuestion: vi.fn(), updateWrongQuestion: vi.fn(),
    deleteWrongQuestion: vi.fn(), listSubjects: vi.fn(), listDictionaryItems: vi.fn(), getKnowledgeTree: vi.fn(),
    listDueReviews: vi.fn(), submitWrongQuestionReview: vi.fn(),
  },
  callApi: vi.fn((request: () => Promise<unknown>) => request()),
}))

const wrong: Wrong = {
  id: '9007199254740993', studentId: '9007199254740994', subjectId: '7', sourceType: WrongSource.Practice,
  questionType: 'SHORT_ANSWER', questionText: '题目', status: WrongStatus.New, version: 3,
}

describe('wrong question and review services', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.listWrongQuestions).mockResolvedValue({ data: { page: 2, pageSize: 10, total: 1, totalPages: 1, items: [wrong] } } as never)
    vi.mocked(api.listDueReviews).mockResolvedValue({ data: { page: 1, pageSize: 10, total: 1, totalPages: 1, items: [wrong] } } as never)
    vi.mocked(api.getWrongQuestion).mockResolvedValue({ data: wrong } as never)
    vi.mocked(api.createWrongQuestion).mockResolvedValue({ data: wrong } as never)
    vi.mocked(api.updateWrongQuestion).mockResolvedValue({ data: wrong } as never)
    vi.mocked(api.deleteWrongQuestion).mockResolvedValue(undefined as never)
    vi.mocked(api.listSubjects).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.listDictionaryItems).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.getKnowledgeTree).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.submitWrongQuestionReview).mockResolvedValue({ data: { reviewId: '88' } } as never)
  })

  it('passes server paging and the string student ID to listWrongQuestions', async () => {
    const result = await wrongQuestionService.list(wrong.studentId, 2, 10)
    expect(api.listWrongQuestions).toHaveBeenCalledWith({ studentId: wrong.studentId, page: 2, pageSize: 10 })
    expect(result.items?.[0]?.id).toBe('9007199254740993')
  })

  it('maps generated create, update, detail and delete operations with string IDs', async () => {
    const create: WrongCreate = { studentId: wrong.studentId, subjectId: '7', sourceType: WrongSource.Practice, questionText: '题目' }
    const update: WrongUpdate = { ...create, questionType: null, version: 3 }
    await wrongQuestionService.create(create)
    await wrongQuestionService.update(wrong.id, update)
    await wrongQuestionService.get(wrong.id)
    await wrongQuestionService.remove(wrong.id)
    expect(api.createWrongQuestion).toHaveBeenCalledWith({ wrongCreate: create })
    expect(api.updateWrongQuestion).toHaveBeenCalledWith({ wrongQuestionId: wrong.id, wrongUpdate: update })
    expect(api.getWrongQuestion).toHaveBeenCalledWith({ wrongQuestionId: wrong.id })
    expect(api.deleteWrongQuestion).toHaveBeenCalledWith({ wrongQuestionId: wrong.id })
  })

  it('loads both dynamic dictionaries including disabled historical items', async () => {
    await wrongQuestionService.listDictionary('question_type')
    await wrongQuestionService.listDictionary('wrong_question_error_type')
    expect(api.listDictionaryItems).toHaveBeenNthCalledWith(1, { dictType: 'question_type', enabledOnly: false })
    expect(api.listDictionaryItems).toHaveBeenNthCalledWith(2, { dictType: 'wrong_question_error_type', enabledOnly: false })
  })

  it('loads enabled subjects and knowledge for the selected subject', async () => {
    await wrongQuestionService.listSubjects()
    await wrongQuestionService.listKnowledge('7')
    expect(api.listSubjects).toHaveBeenCalledWith({ enabledOnly: true })
    expect(api.getKnowledgeTree).toHaveBeenCalledWith({ subjectId: '7', enabledOnly: true })
  })

  it('uses the real due endpoint without calculating due locally', async () => {
    await reviewService.listDue(wrong.studentId, 1, 10)
    expect(api.listDueReviews).toHaveBeenCalledWith({ studentId: wrong.studentId, page: 1, pageSize: 10 })
  })

  it('submits the generated review request with string ID and idempotency key', async () => {
    const request: ReviewCreate = { reviewTime: new Date('2026-08-24T08:00:00Z'), result: ReviewResult.Correct }
    await reviewService.submit(wrong.id, request, 'f4-review-001')
    expect(api.submitWrongQuestionReview).toHaveBeenCalledWith({
      wrongQuestionId: wrong.id, idempotencyKey: 'f4-review-001', reviewCreate: request,
    })
  })
})
