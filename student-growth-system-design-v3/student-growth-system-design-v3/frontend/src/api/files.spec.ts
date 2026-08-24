import { describe, expect, it } from 'vitest'

import { filenameFromContentDisposition } from './files'

describe('download filename', () => {
  it('decodes UTF-8 filenames', () => {
    expect(filenameFromContentDisposition("attachment; filename*=UTF-8''report%20one.pdf", 'download')).toBe('report one.pdf')
  })

  it('removes paths and unsafe filename characters', () => {
    expect(filenameFromContentDisposition('attachment; filename="../bad:name.csv"', 'download')).toBe('bad_name.csv')
  })
})
