import type { KnowledgeLink, Wrong, WrongCreate, WrongSource, WrongStatus, WrongUpdate } from '@/api/generated'
import { WrongSource as WrongSourceValues } from '@/api/generated'

export interface KnowledgeLinkFormRow {
  key: number
  knowledgeId: string
  primary: boolean
  confidence: number | null
}

export interface WrongQuestionFormModel {
  subjectId: string
  sourceType: WrongSource
  questionType: string
  questionText: string
  studentAnswer: string
  correctAnswer: string
  analysisText: string
  errorType: string
  difficulty: number | null
  knowledgePoints: KnowledgeLinkFormRow[]
  status?: WrongStatus
  version?: number
}

let rowKey = 0

export function newKnowledgeLink(): KnowledgeLinkFormRow {
  return { key: ++rowKey, knowledgeId: '', primary: false, confidence: null }
}

export function emptyWrongQuestionForm(): WrongQuestionFormModel {
  return {
    subjectId: '', sourceType: WrongSourceValues.Practice, questionType: '', questionText: '', studentAnswer: '',
    correctAnswer: '', analysisText: '', errorType: '', difficulty: null, knowledgePoints: [],
  }
}

export function wrongQuestionToForm(wrong: Wrong): WrongQuestionFormModel {
  return {
    subjectId: wrong.subjectId,
    sourceType: wrong.sourceType,
    questionType: wrong.questionType ?? '',
    questionText: wrong.questionText,
    studentAnswer: wrong.studentAnswer ?? '',
    correctAnswer: wrong.correctAnswer ?? '',
    analysisText: wrong.analysisText ?? '',
    errorType: wrong.errorType ?? '',
    difficulty: wrong.difficulty ?? null,
    knowledgePoints: (wrong.knowledgePoints ?? []).map((link) => ({
      key: ++rowKey, knowledgeId: link.knowledgeId, primary: link.primary, confidence: link.confidence ?? null,
    })),
    status: wrong.status,
    version: wrong.version,
  }
}

export function validateWrongQuestionForm(
  form: WrongQuestionFormModel,
  enabledQuestionTypes: Set<string>,
  enabledErrorTypes: Set<string>,
): string | null {
  if (!form.subjectId) return '请选择学科'
  if (!form.questionText.trim()) return '请填写题目内容'
  if (form.questionType && !enabledQuestionTypes.has(form.questionType)) return '请选择当前可用的题型'
  if (form.errorType && !enabledErrorTypes.has(form.errorType)) return '请选择当前可用的错误类型'
  const ids = form.knowledgePoints.map((link) => link.knowledgeId)
  if (ids.some((id) => !id)) return '请选择知识点'
  if (new Set(ids).size !== ids.length) return '不能重复关联同一知识点'
  if (form.knowledgePoints.filter((link) => link.primary).length > 1) return '最多设置一个主要知识点'
  if (form.knowledgePoints.some((link) => link.confidence != null && (link.confidence < 0 || link.confidence > 1)))
    return '知识点置信度必须在 0 到 1 之间'
  return null
}

function nullableText(value: string): string | null {
  return value.trim() || null
}

function knowledgeLink(row: KnowledgeLinkFormRow): KnowledgeLink {
  return { knowledgeId: row.knowledgeId, primary: row.primary, confidence: row.confidence }
}

export function buildWrongQuestionRequest(form: WrongQuestionFormModel, studentId: string): WrongCreate | WrongUpdate {
  const create: WrongCreate = {
    studentId,
    subjectId: form.subjectId,
    sourceType: form.sourceType,
    questionType: form.questionType || undefined,
    questionText: form.questionText.trim(),
    studentAnswer: nullableText(form.studentAnswer),
    correctAnswer: nullableText(form.correctAnswer),
    analysisText: nullableText(form.analysisText),
    errorType: nullableText(form.errorType),
    difficulty: form.difficulty,
    knowledgePoints: form.knowledgePoints.map(knowledgeLink),
  }
  if (form.version === undefined) return create
  return { ...create, questionType: form.questionType || null, version: form.version, status: form.status } as WrongUpdate
}
