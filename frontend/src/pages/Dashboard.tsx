import { Box, Card, CardActionArea, CardContent, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { navFor } from '../nav'

export function Dashboard() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <>
      <Typography variant="h4" sx={{ fontWeight: 700 }} gutterBottom>Welcome</Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Signed in as {user.email} ({user.role}). Analytics and alerts arrive in Phase 3.
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' } }}>
        {navFor(user.role).map((n) => (
          <Card key={n.path}>
            <CardActionArea component={RouterLink} to={n.path} sx={{ p: 1 }}>
              <CardContent>
                <Typography variant="h6">{n.label}</Typography>
                <Typography variant="body2" color="text.secondary">{n.description}</Typography>
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Box>
    </>
  )
}
