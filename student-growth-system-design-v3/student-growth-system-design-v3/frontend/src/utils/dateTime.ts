const businessDatePattern = /^\d{4}-\d{2}-\d{2}$/

export function isBusinessDate(value: string): boolean {
  return businessDatePattern.test(value)
}

export function displayBusinessDate(value: string): string {
  if (!isBusinessDate(value)) throw new TypeError('Expected YYYY-MM-DD business date')
  return value
}

export function parseOffsetDateTime(value: string): Date {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) throw new TypeError('Expected offset date-time')
  return parsed
}

export function businessDateToApiDate(value: string): Date {
  if (!isBusinessDate(value)) throw new TypeError('Expected YYYY-MM-DD business date')
  return new Date(`${value}T00:00:00.000Z`)
}

export function formatDate(value?: Date | null): string {
  return value ? value.toISOString().substring(0, 10) : '-'
}

export function formatDateTime(value?: Date | null): string {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(value)
}

export function formatPercent(value?: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}
