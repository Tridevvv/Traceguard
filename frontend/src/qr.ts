/**
 * The link a tag's QR label encodes. Scanning the label with any phone camera opens the public check page,
 * so consumers need no app and no account.
 */
export function checkUrl(tagId: string, origin: string = window.location.origin): string {
  return `${origin}/check/${encodeURIComponent(tagId)}`
}

/**
 * Pulls the tag ID out of a scanned QR value: either one of our check links or a bare tag ID.
 * Returns null for anything else (an unrelated URL, empty text).
 */
export function tagFromScan(value: string): string | null {
  const text = value.trim()
  if (!text) return null
  const match = text.match(/\/check\/([^/?#]+)/)
  if (match) return decodeURIComponent(match[1])
  if (/^https?:\/\//i.test(text)) return null
  return text
}
