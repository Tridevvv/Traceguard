import { describe, expect, it } from 'vitest'
import { decodeToken, isExpired } from './token'

function fakeJwt(claims: object): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.signature`
}

describe('decodeToken', () => {
  it('reads email, role and expiry', () => {
    const user = decodeToken(fakeJwt({ sub: 'a@b.com', role: 'RETAILER', exp: 2_000_000_000 }))
    expect(user).toEqual({ email: 'a@b.com', role: 'RETAILER', expiresAt: 2_000_000_000_000 })
  })

  it('returns null for garbage or incomplete tokens', () => {
    expect(decodeToken('not-a-jwt')).toBeNull()
    expect(decodeToken(fakeJwt({ sub: 'a@b.com' }))).toBeNull()
  })

  it('detects expiry', () => {
    const user = { email: 'a@b.com', role: 'ADMIN' as const, expiresAt: 1000 }
    expect(isExpired(user, 2000)).toBe(true)
    expect(isExpired(user, 500)).toBe(false)
  })
})
