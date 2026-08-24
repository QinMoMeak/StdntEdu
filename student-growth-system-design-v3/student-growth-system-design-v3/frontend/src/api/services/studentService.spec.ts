import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Student, StudentCreate, StudentUpdate } from '@/api/generated'
import { api } from '@/api/client'
import { studentService } from './studentService'

vi.mock('@/api/client', () => ({
  api: {
    listStudents: vi.fn(),
    getStudent: vi.fn(),
    createStudent: vi.fn(),
    updateStudent: vi.fn(),
    listStages: vi.fn(),
    listGrades: vi.fn(),
  },
}))

const student: Student = {
  id: '9007199254740993',
  studentCode: 'STU0001',
  name: '小明',
  currentStageId: '1',
  currentGradeId: '2',
  version: 4,
}

describe('student service', () => {
  beforeEach(() => {
    vi.mocked(api.listStudents).mockResolvedValue({ data: [student] } as never)
    vi.mocked(api.getStudent).mockResolvedValue({ data: student } as never)
    vi.mocked(api.createStudent).mockResolvedValue({ data: student } as never)
    vi.mocked(api.updateStudent).mockResolvedValue({ data: student } as never)
    vi.mocked(api.listStages).mockResolvedValue({ data: [] } as never)
    vi.mocked(api.listGrades).mockResolvedValue({ data: [] } as never)
  })

  it('uses the non-paginated Student[] contract and string IDs', async () => {
    expect(await studentService.list()).toEqual([student])
    await studentService.get(student.id)
    expect(api.getStudent).toHaveBeenCalledWith({ studentId: '9007199254740993' })
  })

  it('does not invent studentCode during creation', async () => {
    const body: StudentCreate = { name: '小明', currentStageId: '1', currentGradeId: '2' }
    await studentService.create(body)
    expect(api.createStudent).toHaveBeenCalledWith({ studentCreate: body })
    expect(body).not.toHaveProperty('studentCode')
  })

  it('passes the generated update model including version', async () => {
    const body: StudentUpdate = { name: '小明', currentStageId: '1', currentGradeId: '2', version: 4 }
    await studentService.update(student.id, body)
    expect(api.updateStudent).toHaveBeenCalledWith({ studentId: student.id, studentUpdate: body })
  })

  it('loads enabled stage and grade reference data', async () => {
    await studentService.listStages()
    await studentService.listGrades()
    expect(api.listStages).toHaveBeenCalledWith({ enabledOnly: true })
    expect(api.listGrades).toHaveBeenCalledWith({ enabledOnly: true })
  })
})
