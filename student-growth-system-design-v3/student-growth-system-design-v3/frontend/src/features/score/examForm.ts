import type {
  Exam,
  ExamCreate,
  ExamUpdate,
  KnowledgeTreeNodeDto,
  ScoreKnowledgeInput,
  SubjectScore,
} from '@/api/generated'
import { ExamType } from '@/api/generated'
import { businessDateToApiDate, formatDate } from '@/utils/dateTime'

export interface KnowledgeScoreFormRow {
  key: number
  knowledgeId: string
  score: number | null
  fullScore: number | null
  questionCount: number | null
  correctCount: number | null
}

export interface SubjectScoreFormRow {
  key: number
  subjectId: string
  score: number | null
  fullScore: number | null
  classRank: number | null
  classSize: number | null
  gradeRank: number | null
  gradeSize: number | null
  knowledgeScores: KnowledgeScoreFormRow[]
}

export interface ExamFormModel {
  examName: string
  academicTermId: string
  examType: ExamType
  examDate: string
  subjects: SubjectScoreFormRow[]
  version?: number
}

let rowKey = 0

export function newKnowledgeRow(): KnowledgeScoreFormRow {
  return { key: ++rowKey, knowledgeId: '', score: null, fullScore: null, questionCount: null, correctCount: null }
}

export function newSubjectRow(): SubjectScoreFormRow {
  return {
    key: ++rowKey,
    subjectId: '',
    score: null,
    fullScore: null,
    classRank: null,
    classSize: null,
    gradeRank: null,
    gradeSize: null,
    knowledgeScores: [],
  }
}

export function emptyExamForm(): ExamFormModel {
  return { examName: '', academicTermId: '', examType: ExamType.DailyTest, examDate: '', subjects: [newSubjectRow()] }
}

export function examToForm(exam: Exam): ExamFormModel {
  return {
    examName: exam.examName,
    academicTermId: exam.academicTermId ?? '',
    examType: exam.examType,
    examDate: formatDate(exam.examDate),
    version: exam.version,
    subjects: exam.subjects.map((subject) => ({
      key: ++rowKey,
      subjectId: subject.subjectId,
      score: subject.score,
      fullScore: subject.fullScore,
      classRank: subject.classRank ?? null,
      classSize: subject.classSize ?? null,
      gradeRank: subject.gradeRank ?? null,
      gradeSize: subject.gradeSize ?? null,
      knowledgeScores: (subject.knowledgeScores ?? []).map((item) => ({
        key: ++rowKey,
        knowledgeId: item.knowledgeId,
        score: item.score,
        fullScore: item.fullScore,
        questionCount: item.questionCount,
        correctCount: item.correctCount,
      })),
    })),
  }
}

function positivePair(rank: number | null, size: number | null, label: string): string | null {
  if (rank == null && size == null) return null
  if (rank == null || size == null) return `${label}排名和人数必须同时填写`
  if (rank < 1 || size < 1 || rank > size) return `${label}排名必须在 1 到人数之间`
  return null
}

export function validateExamForm(form: ExamFormModel): string | null {
  if (!form.examName.trim() || !form.examDate) return '请填写考试名称和日期'
  if (!form.subjects.length) return '至少录入一科成绩'
  const subjectIds = form.subjects.map((row) => row.subjectId)
  if (subjectIds.some((id) => !id)) return '请选择每一行的学科'
  if (new Set(subjectIds).size !== subjectIds.length) return '同一场考试不能重复录入学科'

  for (const row of form.subjects) {
    if (row.score == null || row.fullScore == null || row.score < 0 || row.fullScore <= 0 || row.score > row.fullScore)
      return '成绩必须大于等于 0、满分必须大于 0，且成绩不能超过满分'
    const rankError = positivePair(row.classRank, row.classSize, '班级') ?? positivePair(row.gradeRank, row.gradeSize, '年级')
    if (rankError) return rankError
    const knowledgeIds = row.knowledgeScores.map((item) => item.knowledgeId)
    if (knowledgeIds.some((id) => !id)) return '请选择知识点'
    if (new Set(knowledgeIds).size !== knowledgeIds.length) return '同一学科不能重复录入知识点'
    for (const item of row.knowledgeScores) {
      if (
        item.score == null ||
        item.fullScore == null ||
        item.questionCount == null ||
        item.correctCount == null ||
        item.score < 0 ||
        item.fullScore <= 0 ||
        item.score > item.fullScore ||
        item.questionCount < 0 ||
        item.correctCount < 0 ||
        item.correctCount > item.questionCount
      )
        return '知识点成绩或题目数量不符合要求'
    }
  }
  return null
}

function optionalNumber(value: number | null): number | undefined {
  return value ?? undefined
}

function knowledgeRequest(row: KnowledgeScoreFormRow): ScoreKnowledgeInput {
  return {
    knowledgeId: row.knowledgeId,
    score: row.score!,
    fullScore: row.fullScore!,
    questionCount: row.questionCount!,
    correctCount: row.correctCount!,
  }
}

function subjectRequest(row: SubjectScoreFormRow): SubjectScore {
  return {
    subjectId: row.subjectId,
    score: row.score!,
    fullScore: row.fullScore!,
    classRank: optionalNumber(row.classRank),
    classSize: optionalNumber(row.classSize),
    gradeRank: optionalNumber(row.gradeRank),
    gradeSize: optionalNumber(row.gradeSize),
    knowledgeScores: row.knowledgeScores.map(knowledgeRequest),
  }
}

export function buildExamRequest(form: ExamFormModel, studentId: string): ExamCreate | ExamUpdate {
  const request: ExamCreate = {
    studentId,
    academicTermId: form.academicTermId || undefined,
    examName: form.examName.trim(),
    examType: form.examType,
    examDate: businessDateToApiDate(form.examDate),
    subjects: form.subjects.map(subjectRequest),
  }
  return form.version === undefined ? request : { ...request, version: form.version }
}

export function flattenKnowledge(nodes: KnowledgeTreeNodeDto[], depth = 0): Array<KnowledgeTreeNodeDto & { label: string }> {
  return nodes.flatMap((node) => [
    ...(node.enabled ? [{ ...node, label: `${'　'.repeat(depth)}${node.name}` }] : []),
    ...flattenKnowledge(node.children, depth + 1),
  ])
}
