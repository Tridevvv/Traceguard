import { useState, type FormEvent } from 'react'
import { Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'

export function Landing() {
  const [tagId, setTagId] = useState('')
  const navigate = useNavigate()

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const id = tagId.trim()
    if (id) navigate(`/verify/${encodeURIComponent(id)}`)
  }

  return (
    <Stack spacing={4} sx={{ maxWidth: 720, mx: 'auto', textAlign: 'center' }}>
      <Box>
        <Typography variant="h3" sx={{ fontWeight: 800 }} color="secondary">TraceGuard</Typography>
        <Typography variant="h6" color="text.secondary">
          Every handoff signed. Every product verifiable.
        </Typography>
      </Box>
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }} gutterBottom>Verify a product</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField fullWidth label="Tag ID" placeholder="TG-0123456789AB" value={tagId} onChange={(e) => setTagId(e.target.value)} />
          <Button type="submit" variant="contained" size="large" disabled={!tagId.trim()}>Verify</Button>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          QR scanning and public verification arrive in Phase 3; for now, sign in to verify a tag.
        </Typography>
      </Paper>
    </Stack>
  )
}
