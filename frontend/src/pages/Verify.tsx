import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Button, Chip, Paper, Stack, TextField, Typography } from '@mui/material'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { ChainReport, Checkpoint, ScanEventView } from '../types'
import { useAction } from '../useAction'
import { EventTimeline } from './EventTimeline'

/**
 * Interim verification page: shows whether the tag's signed hash chain is intact.
 * The full AUTHENTIC / SUSPECT / COUNTERFEIT verdict (route, timing, anomalies) arrives in Phase 3.
 */
export function Verify() {
  const { tagId } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [input, setInput] = useState(tagId ?? '')
  // Results are stored with the tag they belong to, so a stale tag's result is never shown for a new tag.
  const [result, setResult] = useState<{ tagId: string; report: ChainReport; events: ScanEventView[]; checkpoints: Checkpoint[] } | null>(null)
  const { run, error } = useAction()
  const signedIn = user !== null

  useEffect(() => {
    if (!tagId || !signedIn) return
    let cancelled = false
    void (async () => {
      const id = encodeURIComponent(tagId)
      const report = await run(() => api<ChainReport>(`/tags/${id}/chain-check`))
      if (!report) return
      const [events, checkpoints] = await Promise.all([
        run(() => api<ScanEventView[]>(`/tags/${id}/events`)),
        run(() => api<Checkpoint[]>('/checkpoints')),
      ])
      if (!cancelled) setResult({ tagId, report, events: events ?? [], checkpoints: checkpoints ?? [] })
    })()
    return () => { cancelled = true }
  }, [tagId, signedIn, run])

  const current = result && result.tagId === tagId ? result : null
  const report = current?.report ?? null
  const events = current?.events ?? []
  const checkpoints = current?.checkpoints ?? []

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (input.trim()) navigate(`/verify/${encodeURIComponent(input.trim())}`)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Verify a Tag</Typography>
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField fullWidth label="Tag ID" value={input} onChange={(e) => setInput(e.target.value)} />
          <Button type="submit" variant="contained" disabled={!input.trim()}>Check</Button>
        </Stack>
      </Paper>

      {tagId && !user && (
        <Alert severity="info" action={<Button color="inherit" size="small" component={RouterLink} to="/login" state={{ from: `/verify/${tagId}` }}>Sign in</Button>}>
          Sign in to verify tag {tagId}. Public verification for consumers arrives in Phase 3.
        </Alert>
      )}
      {error && <Alert severity="error">{error}</Alert>}

      {report && (
        <Paper sx={{ p: 3 }}>
          <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
            <Chip color={report.valid ? 'success' : 'error'} label={report.valid ? 'CHAIN INTACT' : 'CHAIN BROKEN'} />
            <Typography>{report.eventCount} event(s) for {report.tagId}</Typography>
          </Stack>
          {report.eventCount === 0 && <Typography color="text.secondary">No scans recorded for this tag yet.</Typography>}
          {report.problems.map((p) => <Alert key={p} severity="error" sx={{ mt: 1 }}>{p}</Alert>)}
        </Paper>
      )}
      {events.length > 0 && <EventTimeline events={events} checkpoints={checkpoints} />}
    </Stack>
  )
}
