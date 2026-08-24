import { describe, expect, it } from 'vitest'

import { AppApiError, errorDisplayMessage, normalizeApiError } from './errors'
import { FetchError, RequiredError, ResponseError } from './generated/runtime'

describe('API errors', () => {
  it('keeps an existing AppApiError unchanged', async () => {
    const source = new AppApiError({ status: 409, errorCode: 'DATA_VERSION_CONFLICT', message: '版本冲突' })
    expect(await normalizeApiError(source)).toBe(source)
  })

  it('normalizes backend status, code, requestId and field errors', async () => {
    const response = new Response(
      JSON.stringify({
        code: 'VALIDATION_ERROR',
        message: '参数校验失败',
        requestId: 'request-422',
        data: { fieldErrors: [{ field: 'name', message: '不能为空', rejectedValue: '' }] },
      }),
      { status: 422, headers: { 'Content-Type': 'application/json' } },
    )
    const error = await normalizeApiError(new ResponseError(response))
    expect(error).toMatchObject({ status: 422, errorCode: 'VALIDATION_ERROR', requestId: 'request-422' })
    expect(error.fieldErrors).toHaveLength(1)
  })

  it('uses the response header requestId when the body omits it', async () => {
    const response = new Response('{}', { status: 500, headers: { 'X-Request-ID': 'header-request' } })
    expect((await normalizeApiError(new ResponseError(response))).requestId).toBe('header-request')
  })

  it('maps fetch failures to a network error instead of HTTP 500', async () => {
    const error = await normalizeApiError(new FetchError(new TypeError('failed')))
    expect(error).toMatchObject({ status: 0, errorCode: 'NETWORK_ERROR', message: '无法连接本地后端服务' })
  })

  it('maps missing generated parameters to client validation', async () => {
    const error = await normalizeApiError(new RequiredError('studentId'))
    expect(error).toMatchObject({ status: 400, errorCode: 'CLIENT_VALIDATION_ERROR' })
  })

  it('formats a user-safe requestId message', () => {
    const error = new AppApiError({ status: 404, errorCode: 'NOT_FOUND', message: '未找到', requestId: 'request-404' })
    expect(errorDisplayMessage(error)).toBe('未找到（请求编号：request-404）')
  })
})
