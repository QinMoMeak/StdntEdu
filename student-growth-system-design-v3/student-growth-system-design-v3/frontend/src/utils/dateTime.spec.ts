import { describe, expect, it } from 'vitest'

import { displayBusinessDate, parseOffsetDateTime } from './dateTime'

describe('date and time rules', () => {
  it('keeps LocalDate values as YYYY-MM-DD strings', () => {
    expect(displayBusinessDate('2026-08-24')).toBe('2026-08-24')
  })

  it('parses offset timestamps without changing the source contract', () => {
    expect(parseOffsetDateTime('2026-08-24T09:00:00+08:00').toISOString()).toBe('2026-08-24T01:00:00.000Z')
  })
})
