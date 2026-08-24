import type {
  AcademicTermDto,
  Exam,
  ExamCreate,
  ExamType,
  ExamUpdate,
  KnowledgeTreeNodeDto,
  ScorePageResponseAllOfData,
  SubjectDto,
} from '@/api/generated'
import { api, callApi } from '@/api/client'

export interface ScoreFilters {
  academicTermId?: string
  subjectId?: string
  examType?: ExamType
  startDate?: Date
  endDate?: Date
  keyword?: string
  page: number
  pageSize: number
}

function requireData<T>(data: T | undefined, name: string): T {
  if (data === undefined) throw new Error(`${name} response did not contain data`)
  return data
}

export const scoreService = {
  async list(studentId: string, filters: ScoreFilters): Promise<ScorePageResponseAllOfData> {
    const response = await callApi(() => api.listScores({ studentId, ...filters }))
    return response.data ?? { page: filters.page, pageSize: filters.pageSize, total: 0, totalPages: 0, items: [] }
  },

  async get(examId: string): Promise<Exam> {
    return requireData((await callApi(() => api.getExam({ examId }))).data, 'Exam')
  },

  async create(examCreate: ExamCreate): Promise<Exam> {
    return requireData((await callApi(() => api.createExam({ examCreate }))).data, 'Exam')
  },

  async update(examId: string, examUpdate: ExamUpdate): Promise<Exam> {
    return requireData((await callApi(() => api.updateExam({ examId, examUpdate }))).data, 'Exam')
  },

  async remove(examId: string): Promise<void> {
    await callApi(() => api.deleteExam({ examId }))
  },

  async listTerms(studentId: string): Promise<AcademicTermDto[]> {
    return (await callApi(() => api.listAcademicTerms({ studentId, currentOnly: false }))).data ?? []
  },

  async listSubjects(): Promise<SubjectDto[]> {
    return (await callApi(() => api.listSubjects({ enabledOnly: true }))).data ?? []
  },

  async listKnowledge(subjectId: string): Promise<KnowledgeTreeNodeDto[]> {
    return (await callApi(() => api.getKnowledgeTree({ subjectId, enabledOnly: true }))).data ?? []
  },
}
