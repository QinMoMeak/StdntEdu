import { callApi, fetchWithDiagnostics } from './client'
import { ResponseError } from './generated/runtime'

export interface BackendHealth {
  status: string
  application: string
  version: string
  database: string
  flywayVersion: string
  timestamp: string
}

export function getBackendHealth(): Promise<BackendHealth> {
  return callApi(async () => {
    const response = await fetchWithDiagnostics('/internal/health')
    if (!response.ok) throw new ResponseError(response)
    return (await response.json()) as BackendHealth
  })
}
