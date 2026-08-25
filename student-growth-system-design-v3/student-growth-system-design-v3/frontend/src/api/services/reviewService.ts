import type { InlineObject27AllOfData, ReviewCreate, ReviewOutcome } from '@/api/generated'
import { api, callApi } from '@/api/client'

export const reviewService = {
  async listDue(studentId: string, page: number, pageSize: number): Promise<InlineObject27AllOfData> {
    const response = await callApi(() => api.listDueReviews({ studentId, page, pageSize }))
    return response.data ?? { page, pageSize, total: 0, totalPages: 0, items: [] }
  },

  async submit(wrongQuestionId: string, reviewCreate: ReviewCreate, idempotencyKey = crypto.randomUUID()): Promise<ReviewOutcome> {
    const response = await callApi(() =>
      api.submitWrongQuestionReview({ wrongQuestionId, idempotencyKey, reviewCreate }),
    )
    return response.data ?? {}
  },
}
