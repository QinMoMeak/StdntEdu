import { defineStore } from 'pinia'
import { ref } from 'vue'

import { getBackendHealth, type BackendHealth } from '@/api/health'

export type BackendStatus = 'idle' | 'checking' | 'connected' | 'unavailable'

export const useAppStore = defineStore('app', () => {
  const backendStatus = ref<BackendStatus>('idle')
  const backendHealth = ref<BackendHealth | null>(null)

  async function checkBackend(): Promise<void> {
    backendStatus.value = 'checking'
    try {
      backendHealth.value = await getBackendHealth()
      backendStatus.value = 'connected'
    } catch {
      backendHealth.value = null
      backendStatus.value = 'unavailable'
    }
  }

  return { backendStatus, backendHealth, checkBackend }
})
