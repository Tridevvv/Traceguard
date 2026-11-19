import { describe, expect, it } from 'vitest'
import { checkUrl, tagFromScan } from './qr'

describe('QR helpers', () => {
  it('encodes the public check link for a tag', () => {
    expect(checkUrl('TG-DEMO-CLONED', 'https://tg.example')).toBe('https://tg.example/check/TG-DEMO-CLONED')
    expect(checkUrl('TG 1/2', 'https://tg.example')).toBe('https://tg.example/check/TG%201%2F2')
  })

  it('reads a tag back from a scanned link or a bare tag ID', () => {
    expect(tagFromScan('https://tg.example/check/TG%201%2F2')).toBe('TG 1/2')
    expect(tagFromScan('  TG-ABC  ')).toBe('TG-ABC')
  })

  it('ignores unrelated QR codes and empty text', () => {
    expect(tagFromScan('https://example.com/menu')).toBeNull()
    expect(tagFromScan('   ')).toBeNull()
  })
})
