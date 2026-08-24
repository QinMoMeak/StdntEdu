import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Student } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { studentService } from '@/api/services/studentService'
import { useStudentContextStore } from './studentContext'

vi.mock('@/api/services/studentService', () => ({
  studentService: { list: vi.fn(), get: vi.fn() },
}))

const student: Student = {
  id: '9007199254740993',
  studentCode: 'STU0001',
  name: '小明',
  currentStageId: '1',
  currentGradeId: '2',
  version: 0,
}

describe('student context store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(studentService.list).mockResolvedValue([student])
    vi.mocked(studentService.get).mockResolvedValue(student)
  })

  it('starts without a hard-coded student or storage ID', () => {
    expect(useStudentContextStore().currentStudentId).toBeNull()
  })

  it('restores a valid string ID and student detail', async () => {
    localStorage.setItem('stdntedu.currentStudentId', student.id)
    setActivePinia(createPinia())
    const store = useStudentContextStore()
    await store.initialize()
    expect(studentService.get).toHaveBeenCalledWith(student.id)
    expect(store.currentStudent).toEqual(student)
    expect(typeof store.currentStudentId).toBe('string')
    expect(store.validationState).toBe('valid')
  })

  it('clears a stored ID only when getStudent returns 404', async () => {
    localStorage.setItem('stdntedu.currentStudentId', 'missing')
    setActivePinia(createPinia())
    vi.mocked(studentService.get).mockRejectedValue(
      new AppApiError({ status: 404, errorCode: 'NOT_FOUND', message: 'not found' }),
    )
    const store = useStudentContextStore()
    await expect(store.restoreCurrentStudent()).resolves.toBe(false)
    expect(store.currentStudentId).toBeNull()
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBeNull()
    expect(store.validationState).toBe('invalid')
  })

  it('preserves storage when the backend is unavailable', async () => {
    localStorage.setItem('stdntedu.currentStudentId', student.id)
    setActivePinia(createPinia())
    vi.mocked(studentService.get).mockRejectedValue(
      new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'offline' }),
    )
    const store = useStudentContextStore()
    await expect(store.restoreCurrentStudent()).rejects.toMatchObject({ errorCode: 'NETWORK_ERROR' })
    expect(store.currentStudentId).toBe(student.id)
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBe(student.id)
    expect(store.validationState).toBe('unavailable')
  })

  it('switches and explicitly clears the current student', () => {
    const store = useStudentContextStore()
    store.selectStudent(student)
    expect(store.currentStudentId).toBe(student.id)
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBe(student.id)
    store.clearCurrentStudent()
    expect(store.currentStudentId).toBeNull()
    expect(store.currentStudent).toBeNull()
  })

  it('does not automatically select the first listed student', async () => {
    const store = useStudentContextStore()
    await store.loadStudents()
    expect(store.students).toEqual([student])
    expect(store.currentStudentId).toBeNull()
  })

  it('keeps current detail synchronized after an update', () => {
    const store = useStudentContextStore()
    store.students = [student]
    store.selectStudent(student)
    store.replaceStudent({ ...student, name: '小明（更新）', version: 1 })
    expect(store.currentStudent?.name).toBe('小明（更新）')
    expect(store.currentStudent?.version).toBe(1)
  })
})
