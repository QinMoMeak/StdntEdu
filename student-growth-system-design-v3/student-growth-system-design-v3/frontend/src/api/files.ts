function safeFilename(value: string): string {
  const leaf = value.split(/[\\/]/).pop() ?? ''
  // Control characters are rejected before the server filename reaches the DOM.
  // eslint-disable-next-line no-control-regex
  return leaf.replace(/[\u0000-\u001f<>:"|?*]/g, '_').trim() || 'download'
}

export function filenameFromContentDisposition(header: string | null, fallback: string): string {
  const encoded = header?.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plain = header?.match(/filename="?([^";]+)"?/i)?.[1]
  let candidate = plain
  if (encoded) {
    try {
      candidate = decodeURIComponent(encoded)
    } catch {
      candidate = encoded
    }
  }
  return safeFilename(candidate ?? fallback)
}

export async function downloadResponse(response: Response, fallbackName: string): Promise<void> {
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filenameFromContentDisposition(response.headers.get('Content-Disposition'), fallbackName)
  anchor.click()
  URL.revokeObjectURL(url)
}
