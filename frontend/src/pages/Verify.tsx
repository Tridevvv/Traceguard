import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { api, download } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { Checkpoint, Role, ScanEventView, Verdict } from '../types'
import { useAction } from '../useAction'
import { tagReportPath } from '../api/reports'
import { EventTimeline } from './EventTimeline'
import { VerdictPanel } from './VerdictPanel'

/** Roles the server lets export a product-history report (a manufacturer only for its own tags). */
const REPORT_ROLES: Role[] = ['ADMIN', 'AUDITOR', 'MANUFACTURER']

/**
 * Staff verification page: shows the full AUTHENTIC / SUSPECT / COUNTERFEIT verdict from
 * GET /api/verify/{tagId} with its reasons, followed by the ledger timeline with hashes.
 */
export function Verify() {
  const { tagId } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const [input, setInput] = useState(tagId ?? '')
  // Results are stored with the tag they belong to, so a stale tag's result is never shown for a new tag.
  const [result, setResult] = useState<{ tagId: string; verdict: Verdict; events: ScanEventView[]; checkpoints: Checkpoint[] } | null>(null)
  const { run, busy, error } = useAction()
  const report = useAction()
  const signedIn = user !== null
  const canReport = user !== null && REPORT_ROLES.includes(user.role)

  useEffect(() => {
    if (!tagId || !signedIn) return
    let cancelled = false
    void (async () => {
      const id = encodeURIComponent(tagId)
      const verdict = await run(() => api<Verdict>(`/verify/${id}`))
      if (!verdict) return
      // The timeline is extra detail: if it cannot be loaded, still show the verdict.
      const [events, checkpoints] = await Promise.all([
        api<ScanEventView[]>(`/tags/${id}/events`).catch(() => [] as ScanEventView[]),
        api<Checkpoint[]>('/checkpoints').catch(() => [] as Checkpoint[]),
      ])
      if (!cancelled) setResult({ tagId, verdict, events, checkpoints })
    })()
    return () => { cancelled = true }
  }, [tagId, signedIn, run])

  const current = result && result.tagId === tagId ? result : null

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
          <Button type="submit" variant="contained" disabled={!input.trim() || busy}>
            {busy ? 'Checking…' : 'Check'}
          </Button>
        </Stack>
      </Paper>

      {tagId && !user && (
        <Alert
          severity="info"
          action={
            <Stack direction="row" spacing={1}>
              <Button color="inherit" size="small" component={RouterLink} to={`/check/${encodeURIComponent(tagId)}`}>Public check</Button>
              <Button color="inherit" size="small" component={RouterLink} to="/login" state={{ from: `/verify/${tagId}` }}>Sign in</Button>
            </Stack>
          }
        >
          Sign in for the full staff view of tag {tagId}, or use the public check, which needs no account.
        </Alert>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {report.error && <Alert severity="error">{report.error}</Alert>}

      {current && <VerdictPanel verdict={current.verdict} />}
      {current && canReport && (
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" size="small" disabled={report.busy}
            onClick={() => void report.run(() => download(tagReportPath(current.tagId, 'csv'), 'traceguard-tag.csv'))}>
            Download history (CSV)
          </Button>
          <Button variant="outlined" size="small" disabled={report.busy}
            onClick={() => void report.run(() => download(tagReportPath(current.tagId, 'pdf'), 'traceguard-tag.pdf'))}>
            Download history (PDF)
          </Button>
        </Stack>
      )}
      {current && current.events.length > 0 && <EventTimeline events={current.events} checkpoints={current.checkpoints} />}
    </Stack>
  )
}
