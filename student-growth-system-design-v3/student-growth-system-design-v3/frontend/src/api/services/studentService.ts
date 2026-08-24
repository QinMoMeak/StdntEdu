import type { GradeDto, StageDto, Student, StudentCreate, StudentUpdate } from '@/api/generated'
import { api } from '@/api/client'
import { normalizeApiError } from '@/api/errors'

async function call<T>(request: Promise<T>): Promise<T> {
  try {
    return await request
  } catch (error) {
    throw await normalizeApiError(error)
  }
}

export const studentService = {
  async list(): Promise<Student[]> {
    return (await call(api.listStudents())).data ?? []
  },

  async get(studentId: string): Promise<Student> {
    const student = (await call(api.getStudent({ studentId }))).data
    if (!student) throw new Error('Student response did not contain data')
    return student
  },

  async create(studentCreate: StudentCreate): Promise<Student> {
    const student = (await call(api.createStudent({ studentCreate }))).data
    if (!student) throw new Error('Student response did not contain data')
    return student
  },

  async update(studentId: string, studentUpdate: StudentUpdate): Promise<Student> {
    const student = (await call(api.updateStudent({ studentId, studentUpdate }))).data
    if (!student) throw new Error('Student response did not contain data')
    return student
  },

  async listStages(): Promise<StageDto[]> {
    return (await call(api.listStages({ enabledOnly: true }))).data ?? []
  },

  async listGrades(): Promise<GradeDto[]> {
    return (await call(api.listGrades({ enabledOnly: true }))).data ?? []
  },
}
