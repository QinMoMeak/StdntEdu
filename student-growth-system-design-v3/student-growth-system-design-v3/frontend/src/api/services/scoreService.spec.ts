import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Exam, ExamCreate, ExamUpdate } from '@/api/generated'
import { ExamType } from '@/api/generated'
import { api } from '@/api/client'
import { scoreService } from './scoreService'

vi.mock('@/api/client', () => ({
  api: {
    listScores: vi.fn(), getExam: vi.fn(), createExam: vi.fn(), updateExam: vi.fn(), deleteExam: vi.fn(),
    listAcademicTerms: vi.fn(), listSubjects: vi.fn(), getKnowledgeTree: vi.fn(),
  },
  callApi: vi.fn((request: () => Promise<unknown>) => request()),
}))

const exam: Exam = {
  id: '9007199254740993', studentId: '9007199254740995', examName: '期中考试', examType: ExamType.Midterm,
  examDate: new Date('2026-05-10T00:00:00Z'), subjects: [{ subjectId: '7', score: 88, fullScore: 100 }], version: 3,
}

describe('score service', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.listScores).mockResolvedValue({ data: { page: 1, pageSize: 20, total: 1, totalPages: 1, items: [] } } as never)
    vi.mocked(api.getExam).mockResolvedValue({ data: exam } as never)
    vi.mocked(api.createExam).mockResolvedValue({ data: exam } as never)
    vi.mocked(api.updateExam).mockResolvedValue({ data: exam } as never)
    vi.mocked(api.deleteExam).mockResolvedValue(undefined as never)
    vi.mocked(api.listAcademicTerms).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.listSubjects).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.getKnowledgeTree).mockResolvedValue({ data: [] } as never)
  })

  it('passes real server paging, filters and string student ID', async () => {
    await scoreService.list(exam.studentId, { subjectId: '7', page: 2, pageSize: 10 })
    expect(api.listScores).toHaveBeenCalledWith({ studentId: exam.studentId, subjectId: '7', page: 2, pageSize: 10 })
  })

  it('maps create and update to generated request models', async () => {
    const create: ExamCreate = { studentId: exam.studentId, examName: exam.examName, examType: exam.examType, examDate: exam.examDate, subjects: exam.subjects }
    await scoreService.create(create)
    await scoreService.update(exam.id, { ...create, version: 3 } satisfies ExamUpdate)
    expect(api.createExam).toHaveBeenCalledWith({ examCreate: create })
    expect(api.updateExam).toHaveBeenCalledWith({ examId: exam.id, examUpdate: expect.objectContaining({ version: 3 }) })
  })

  it('uses the real detail and delete operations with string exam IDs', async () => {
    expect((await scoreService.get(exam.id)).id).toBe('9007199254740993')
    await scoreService.remove(exam.id)
    expect(api.deleteExam).toHaveBeenCalledWith({ examId: exam.id })
  })

  it('loads terms, enabled subjects and enabled knowledge from public APIs', async () => {
    await scoreService.listTerms(exam.studentId)
    await scoreService.listSubjects()
    await scoreService.listKnowledge('7')
    expect(api.listAcademicTerms).toHaveBeenCalledWith({ studentId: exam.studentId, currentOnly: false })
    expect(api.listSubjects).toHaveBeenCalledWith({ enabledOnly: true })
    expect(api.getKnowledgeTree).toHaveBeenCalledWith({ subjectId: '7', enabledOnly: true })
  })
})
