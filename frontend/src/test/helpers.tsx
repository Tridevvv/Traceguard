import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../auth/AuthProvider'
import { tokenStore } from '../api/client'
import type { Role } from '../types'

/** An unsigned JWT the frontend can decode; the backend is faked, so the signature is never checked. */
export function fakeJwt(role: Role): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
  return `${b64({ alg: 'HS256' })}.${b64({ sub: 'user@test.local', role, exp: Math.floor(Date.now() / 1000) + 3600 })}.sig`
}

export function signInAs(role: Role) {
  tokenStore.set(fakeJwt(role))
}

/** Renders the whole app at a path, with routing and auth, as a user would see it. */
export function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers })
}

type Handler = (url: string, init?: RequestInit) => Response | Promise<Response>

/** Replaces fetch with a fake backend. Unknown URLs answer 404 so a wrong path fails the test visibly. */
export function stubFetch(handler: Handler) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => handler(url, init))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
