const TOKEN_KEY = 'traceguard.token'

/** The JWT lives in sessionStorage: it is cleared when the tab closes. Storage can throw (private mode), so guard it. */
export const tokenStore = {
  get(): string | null {
    try {
      return sessionStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string) {
    try {
      sessionStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* keep working with the in-memory state only */
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(TOKEN_KEY)
    } catch {
      /* ignore */
    }
  },
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let onUnauthorized: (() => void) | null = null

/** Lets the auth provider log the user out when the server says the token is no longer valid. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

const FRIENDLY: Record<number, string> = {
  401: 'You are not signed in or your session has expired.',
  403: 'Your role is not allowed to do this.',
}

export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = tokenStore.get()
  const hasBody = init.body !== undefined
  const res = await fetch(`/api${path}`, {
    method: init.method ?? 'GET',
    headers: {
      ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: hasBody ? JSON.stringify(init.body) : undefined,
  })

  if (!res.ok) {
    let message = FRIENDLY[res.status] ?? res.statusText
    try {
      const data = await res.json()
      if (typeof data?.message === 'string' && data.message) message = data.message
    } catch {
      /* body was not JSON */
    }
    if (res.status === 401 && token) onUnauthorized?.()
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
