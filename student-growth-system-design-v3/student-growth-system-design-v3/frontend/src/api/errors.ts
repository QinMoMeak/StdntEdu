import { FetchError, RequiredError, ResponseError } from './generated/runtime'

export interface ApiFieldError {
  field: string
  message: string
  rejectedValue?: unknown
}

interface BackendErrorEnvelope {
  code?: string
  message?: string
  requestId?: string
  data?: { fieldErrors?: ApiFieldError[] }
}

export class AppApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly requestId?: string
  readonly fieldErrors: ApiFieldError[]

  constructor(options: {
    status: number
    errorCode: string
    message: string
    requestId?: string
    fieldErrors?: ApiFieldError[]
  }) {
    super(options.message)
    this.name = 'AppApiError'
    this.status = options.status
    this.errorCode = options.errorCode
    this.requestId = options.requestId
    this.fieldErrors = options.fieldErrors ?? []
  }
}

async function readErrorEnvelope(response: Response): Promise<BackendErrorEnvelope | undefined> {
  try {
    return (await response.clone().json()) as BackendErrorEnvelope
  } catch {
    return undefined
  }
}

export async function normalizeApiError(error: unknown): Promise<AppApiError> {
  if (error instanceof AppApiError) return error

  if (error instanceof ResponseError) {
    const body = await readErrorEnvelope(error.response)
    return new AppApiError({
      status: error.response.status,
      errorCode: body?.code ?? 'HTTP_ERROR',
      message: body?.message ?? '请求未能完成',
      requestId: body?.requestId ?? error.response.headers.get('X-Request-ID') ?? undefined,
      fieldErrors: body?.data?.fieldErrors,
    })
  }

  if (error instanceof RequiredError) {
    return new AppApiError({ status: 400, errorCode: 'CLIENT_VALIDATION_ERROR', message: '请求参数不完整' })
  }

  if (error instanceof FetchError || error instanceof TypeError) {
    return new AppApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: '无法连接本地后端服务' })
  }

  return new AppApiError({ status: 0, errorCode: 'CLIENT_ERROR', message: '请求处理失败' })
}

export function errorDisplayMessage(error: AppApiError): string {
  return error.requestId ? `${error.message}（请求编号：${error.requestId}）` : error.message
}
