import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { Student } from '@/api/generated'
import { AppApiError } from '@/api/errors'
import { studentService } from '@/api/services/studentService'

const storageKey = 'stdntedu.currentStudentId'

function storedStudentId(): string | null {
  const value = localStorage.getItem(storageKey)?.trim()
  return value || null
}

export const useStudentContextStore = defineStore('studentContext', () => {
  const currentStudentId = ref<string | null>(storedStudentId())
  const currentStudent = ref<Student | null>(null)
  const students = ref<Student[]>([])
  const loading = ref(false)
  const initialized = ref(false)
  const validationState = ref<'idle' | 'validating' | 'valid' | 'invalid' | 'unavailable'>('idle')

  function selectStudent(student: Student | null): void {
    currentStudent.value = student
    currentStudentId.value = student?.id ?? null
    if (student) localStorage.setItem(storageKey, student.id)
    else localStorage.removeItem(storageKey)
  }

  function clearCurrentStudent(): void {
    selectStudent(null)
    validationState.value = 'idle'
  }

  async function loadStudents(): Promise<Student[]> {
    loading.value = true
    try {
      students.value = await studentService.list()
      initialized.value = true
      return students.value
    } finally {
      loading.value = false
    }
  }

  async function restoreCurrentStudent(): Promise<boolean> {
    if (!currentStudentId.value) return false
    validationState.value = 'validating'
    try {
      currentStudent.value = await studentService.get(currentStudentId.value)
      validationState.value = 'valid'
      return true
    } catch (error) {
      if (error instanceof AppApiError && error.status === 404) {
        selectStudent(null)
        validationState.value = 'invalid'
        return false
      }
      validationState.value = 'unavailable'
      throw error
    }
  }

  async function initialize(): Promise<void> {
    await loadStudents()
    if (currentStudentId.value) await restoreCurrentStudent()
  }

  function replaceStudent(student: Student): void {
    const index = students.value.findIndex((item) => item.id === student.id)
    if (index >= 0) students.value[index] = student
    if (currentStudentId.value === student.id) selectStudent(student)
  }

  return {
    currentStudentId,
    currentStudent,
    students,
    loading,
    initialized,
    validationState,
    selectStudent,
    clearCurrentStudent,
    loadStudents,
    restoreCurrentStudent,
    initialize,
    replaceStudent,
  }
})
