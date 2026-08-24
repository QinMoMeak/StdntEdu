import { ElMessage } from 'element-plus'

import { errorDisplayMessage, normalizeApiError } from './errors'

export async function handleApiError(error: unknown): Promise<void> {
  const normalized = await normalizeApiError(error)
  ElMessage.error(errorDisplayMessage(normalized))
  console.error('[api-error]', {
    status: normalized.status,
    errorCode: normalized.errorCode,
    requestId: normalized.requestId,
    fieldErrors: normalized.fieldErrors,
  })
}
