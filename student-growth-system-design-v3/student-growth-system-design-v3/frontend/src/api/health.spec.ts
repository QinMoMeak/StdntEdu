import { describe, expect, it, vi } from 'vitest'

import { getBackendHealth } from './health'

describe('backend health', () => {
  it('reads a successful Local V1 health response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(
          JSON.stringify({
            status: 'UP',
            application: 'student-growth-baseline',
            version: '0.2.0-SNAPSHOT',
            database: 'UP',
            flywayVersion: '23',
            timestamp: '2026-08-24T09:00:00+08:00',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    expect((await getBackendHealth()).flywayVersion).toBe('23')
  })

  it('reports an unavailable backend as a network error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('offline'))))
    await expect(getBackendHealth()).rejects.toMatchObject({ errorCode: 'NETWORK_ERROR', status: 0 })
  })
})
