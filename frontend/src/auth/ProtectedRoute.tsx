import type { ReactNode } from 'react'
import { Alert, Container } from '@mui/material'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../types'
import { useAuth } from './useAuth'

/** Sends signed-out visitors to /login, and shows a notice if the role may not open the page. */
export function ProtectedRoute({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (roles && !roles.includes(user.role)) {
    return (
      <Container sx={{ py: 4 }}>
        <Alert severity="warning">Your role ({user.role}) cannot open this page.</Alert>
      </Container>
    )
  }
  return <>{children}</>
}
