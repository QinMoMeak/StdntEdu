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
