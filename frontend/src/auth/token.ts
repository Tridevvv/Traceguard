import type { Role } from '../types'

export interface SessionUser {
  email: string
  role: Role
  expiresAt: number
}

/** Reads the claims of a JWT for display and routing. The server still checks the signature on every request. */
export function decodeToken(token: string): SessionUser | null {
  try {
    const payload = token.split('.')[1]
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const claims = JSON.parse(json) as { sub?: string; role?: Role; exp?: number }
    if (!claims.sub || !claims.role || !claims.exp) return null
    return { email: claims.sub, role: claims.role, expiresAt: claims.exp * 1000 }
  } catch {
    return null
  }
}

export function isExpired(user: SessionUser, now = Date.now()): boolean {
  return user.expiresAt <= now
}
