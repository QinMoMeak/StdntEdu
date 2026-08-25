import type {
  DictionaryItemDto,
  InlineObject27AllOfData,
  KnowledgeTreeNodeDto,
  SubjectDto,
  Wrong,
  WrongCreate,
  WrongUpdate,
} from '@/api/generated'
import { api, callApi } from '@/api/client'

function requireData<T>(data: T | undefined, name: string): T {
  if (data === undefined) throw new Error(`${name} response did not contain data`)
  return data
}

export const wrongQuestionService = {
  async list(studentId: string, page: number, pageSize: number): Promise<InlineObject27AllOfData> {
    const response = await callApi(() => api.listWrongQuestions({ studentId, page, pageSize }))
    return response.data ?? { page, pageSize, total: 0, totalPages: 0, items: [] }
  },

  async get(wrongQuestionId: string): Promise<Wrong> {
    return requireData((await callApi(() => api.getWrongQuestion({ wrongQuestionId }))).data, 'WrongQuestion')
  },

  async create(wrongCreate: WrongCreate): Promise<Wrong> {
    return requireData((await callApi(() => api.createWrongQuestion({ wrongCreate }))).data, 'WrongQuestion')
  },

  async update(wrongQuestionId: string, wrongUpdate: WrongUpdate): Promise<Wrong> {
    return requireData((await callApi(() => api.updateWrongQuestion({ wrongQuestionId, wrongUpdate }))).data, 'WrongQuestion')
  },

  async remove(wrongQuestionId: string): Promise<void> {
    await callApi(() => api.deleteWrongQuestion({ wrongQuestionId }))
  },

  async listSubjects(): Promise<SubjectDto[]> {
    return (await callApi(() => api.listSubjects({ enabledOnly: true }))).data ?? []
  },

  async listDictionary(dictType: string): Promise<DictionaryItemDto[]> {
    return (await callApi(() => api.listDictionaryItems({ dictType, enabledOnly: false }))).data ?? []
  },

  async listKnowledge(subjectId: string): Promise<KnowledgeTreeNodeDto[]> {
    return (await callApi(() => api.getKnowledgeTree({ subjectId, enabledOnly: true }))).data ?? []
  },
}
