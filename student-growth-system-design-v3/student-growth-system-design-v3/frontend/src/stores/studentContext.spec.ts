import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useStudentContextStore } from './studentContext'

describe('student context store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('starts without a hard-coded student', () => {
    expect(useStudentContextStore().currentStudentId).toBeNull()
  })

  it('stores BIGINT API IDs as strings', () => {
    const store = useStudentContextStore()
    store.setCurrentStudent('9007199254740993')
    expect(store.currentStudentId).toBe('9007199254740993')
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBe('9007199254740993')
  })

  it('restores the current student from localStorage', () => {
    localStorage.setItem('stdntedu.currentStudentId', '42')
    setActivePinia(createPinia())
    expect(useStudentContextStore().currentStudentId).toBe('42')
  })

  it('clears a student context that no longer exists', async () => {
    const store = useStudentContextStore()
    store.setCurrentStudent('42')
    expect(await store.validateCurrentStudent(vi.fn(async () => false))).toBe(false)
    expect(store.currentStudentId).toBeNull()
    expect(localStorage.getItem('stdntedu.currentStudentId')).toBeNull()
  })
})
