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

  if (!res.ok) throw await toError(res, token)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** Turns a failed response into an ApiError with the server's message, and logs out on an expired token. */
async function toError(res: Response, token: string | null): Promise<ApiError> {
  let message = FRIENDLY[res.status] ?? res.statusText
  try {
    const data = await res.json()
    if (typeof data?.message === 'string' && data.message) message = data.message
  } catch {
    /* body was not JSON */
  }
  if (res.status === 401 && token) onUnauthorized?.()
  return new ApiError(res.status, message)
}

/** Reads the file name from a Content-Disposition header such as `attachment; filename="report.csv"`. */
export function fileNameFrom(header: string | null, fallback: string): string {
  const match = header?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)
  return match ? decodeURIComponent(match[1]) : fallback
}

/**
 * Downloads a file from the API (CSV/PDF reports). A plain link cannot send the JWT header,
 * so the file is fetched with the token and saved through a temporary object URL.
 * Returns the saved file name.
 */
export async function download(path: string, fallbackName: string): Promise<string> {
  const token = tokenStore.get()
  const res = await fetch(`/api${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!res.ok) throw await toError(res, token)
  const name = fileNameFrom(res.headers.get('Content-Disposition'), fallbackName)
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  return name
}
