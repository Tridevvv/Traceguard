import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from '../api/client'
import type { Role } from '../types'
import { AuthContext } from './AuthContext'
import { decodeToken, isExpired } from './token'

function initialToken(): string | null {
  const token = tokenStore.get()
  const user = token ? decodeToken(token) : null
  return token && user && !isExpired(user) ? token : null
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(initialToken)

  const logout = useCallback(() => {
    tokenStore.clear()
    setToken(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<{ token: string; role: Role }>('/auth/login', { method: 'POST', body: { email, password } })
    tokenStore.set(res.token)
    setToken(res.token)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const user = useMemo(() => (token ? decodeToken(token) : null), [token])
  const value = useMemo(() => ({ user, login, logout }), [user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
