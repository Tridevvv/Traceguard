import { AppBar, Box, Button, Chip, Container, Toolbar, Typography } from '@mui/material'
import { Link as RouterLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/useAuth'
import { navFor } from './nav'

export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="static" color="secondary" elevation={0}>
        <Toolbar sx={{ gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="h6" component={RouterLink} to="/" sx={{ color: 'inherit', textDecoration: 'none', fontWeight: 700, mr: 2 }}>
            TraceGuard
          </Typography>
          {user && (
            <>
              <Button color="inherit" component={RouterLink} to="/dashboard">Dashboard</Button>
              {navFor(user.role).map((n) => (
                <Button key={n.path} color="inherit" component={RouterLink} to={n.path}>{n.label}</Button>
              ))}
            </>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {user ? (
            <>
              <Chip label={`${user.email} · ${user.role}`} size="small" sx={{ color: 'white', borderColor: 'rgba(255,255,255,.5)' }} variant="outlined" />
              <Button color="inherit" onClick={() => { logout(); navigate('/') }}>Sign out</Button>
            </>
          ) : (
            <Button color="inherit" component={RouterLink} to="/login">Sign in</Button>
          )}
        </Toolbar>
      </AppBar>
      <Container sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
