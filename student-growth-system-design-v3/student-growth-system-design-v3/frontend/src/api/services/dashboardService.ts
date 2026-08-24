import type { DashboardDto } from '@/api/generated'
import { api } from '@/api/client'
import { normalizeApiError } from '@/api/errors'

export const dashboardService = {
  async get(studentId: string): Promise<DashboardDto> {
    try {
      const dashboard = (await api.getDashboard({ studentId, timezone: 'Asia/Shanghai' })).data
      if (!dashboard) throw new Error('Dashboard response did not contain data')
      return dashboard
    } catch (error) {
      throw await normalizeApiError(error)
    }
  },
}
