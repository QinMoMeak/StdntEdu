import { defineStore } from 'pinia'
import { ref } from 'vue'

const storageKey = 'stdntedu.currentStudentId'

function storedStudentId(): string | null {
  const value = localStorage.getItem(storageKey)?.trim()
  return value || null
}

export const useStudentContextStore = defineStore('studentContext', () => {
  const currentStudentId = ref<string | null>(storedStudentId())

  function setCurrentStudent(id: string | null): void {
    const normalized = id?.trim() || null
    currentStudentId.value = normalized
    if (normalized) localStorage.setItem(storageKey, normalized)
    else localStorage.removeItem(storageKey)
  }

  async function validateCurrentStudent(exists: (id: string) => Promise<boolean>): Promise<boolean> {
    if (!currentStudentId.value) return false
    if (await exists(currentStudentId.value)) return true
    setCurrentStudent(null)
    return false
  }

  return { currentStudentId, setCurrentStudent, validateCurrentStudent }
})
