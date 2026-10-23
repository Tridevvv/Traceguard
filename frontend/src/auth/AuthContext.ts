import { createContext } from 'react'
import type { SessionUser } from './token'

export interface AuthState {
  user: SessionUser | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthState | null>(null)
