import { DefaultApi } from './generated/apis/DefaultApi'
import { Configuration } from './generated/runtime'
import { normalizeApiError } from './errors'

export const apiBasePath = import.meta.env.VITE_API_BASE_URL || '/api/v1'

function requestPath(input: RequestInfo | URL): string {
  const value = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  return new URL(value, globalThis.location?.origin ?? 'http://localhost').pathname
}

export const fetchWithDiagnostics: typeof fetch = async (input, init) => {
  const started = performance.now()
  try {
    const response = await globalThis.fetch(input, init)
    if (import.meta.env.DEV) {
      console.debug('[api]', {
        method: init?.method ?? 'GET',
        path: requestPath(input),
        status: response.status,
        requestId: response.headers.get('X-Request-ID'),
        durationMs: Math.round(performance.now() - started),
      })
    }
    return response
  } catch (error) {
    if (import.meta.env.DEV) {
      console.debug('[api]', {
        method: init?.method ?? 'GET',
        path: requestPath(input),
        status: 'NETWORK_ERROR',
        durationMs: Math.round(performance.now() - started),
      })
    }
    throw error
  }
}

export const apiConfiguration = new Configuration({ basePath: apiBasePath, fetchApi: fetchWithDiagnostics })
export const api = new DefaultApi(apiConfiguration)

export async function callApi<T>(request: () => Promise<T>): Promise<T> {
  try {
    return await request()
  } catch (error) {
    throw await normalizeApiError(error)
  }
}
