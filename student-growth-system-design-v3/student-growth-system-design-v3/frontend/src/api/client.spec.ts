import { describe, expect, it, vi } from 'vitest'

import { apiBasePath, callApi } from './client'
import { DefaultApi } from './generated/apis/DefaultApi'
import { Configuration } from './generated/runtime'

describe('generated API client', () => {
  it('uses the local Vite proxy base path by default', () => {
    expect(apiBasePath).toBe('/api/v1')
  })

  it('calls a representative operation without Authorization', async () => {
    const fetchApi = vi.fn(async () =>
      new Response(
        JSON.stringify({ code: 'OK', message: 'success', requestId: 'request-stages', timestamp: '2026-08-24T09:00:00+08:00', data: [] }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    const client = new DefaultApi(new Configuration({ basePath: '/api/v1', fetchApi }))

    await client.listStages()

    const headers = new Headers(fetchApi.mock.calls[0]?.[1]?.headers)
    expect(headers.has('Authorization')).toBe(false)
  })

  it('normalizes errors crossing the handwritten adapter', async () => {
    await expect(callApi(() => Promise.reject(new TypeError('offline')))).rejects.toMatchObject({
      errorCode: 'NETWORK_ERROR',
    })
  })
})
