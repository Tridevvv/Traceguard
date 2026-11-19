import { useState, type FormEvent } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { download } from '../api/client'
import { auditReportPath, isoDay, tagReportPath, type ReportFormat as Format } from '../api/reports'
import { useAuth } from '../auth/useAuth'
import { useAction } from '../useAction'

/** Keeps "Download CSV/PDF" on one line next to a full-width field. */
const NO_WRAP_BUTTONS = { '& .MuiButton-root': { whiteSpace: 'nowrap', flexShrink: 0 } }

const AUDIT_TYPES = [
  { value: 'actions', label: 'User activity (audit log)' },
  { value: 'alerts', label: 'Alerts' },
  { value: 'events', label: 'Ledger events' },
]

/**
 * Report downloads (FR-13). Admins and auditors export audit reports for a date range; admins, auditors and
 * manufacturers export one product's history (a manufacturer only for its own tags, enforced by the server).
 */
export function Reports() {
  const { user } = useAuth()
  const canAudit = user?.role === 'ADMIN' || user?.role === 'AUDITOR'
  const [type, setType] = useState('actions')
  // Default range: the last 30 days, matching the server's default. Computed once, on first render.
  const [from, setFrom] = useState(() => isoDay(new Date(Date.now() - 29 * 86_400_000)))
  const [to, setTo] = useState(() => isoDay(new Date()))
  const [tagId, setTagId] = useState('')
  const [saved, setSaved] = useState<string | null>(null)
  const { run, busy, error } = useAction()

  const get = async (path: string, fallback: string) => {
    setSaved(null)
    const name = await run(() => download(path, fallback))
    if (name) setSaved(name)
  }

  const auditDownload = (format: Format) => (e?: FormEvent) => {
    e?.preventDefault()
    void get(auditReportPath(type, format, from, to), `traceguard-${type}.${format}`)
  }

  const tagDownload = (format: Format) => () => {
    if (tagId.trim()) void get(tagReportPath(tagId.trim(), format), `traceguard-tag.${format}`)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Reports</Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {saved && <Alert severity="success">Downloaded {saved}</Alert>}

      {canAudit && (
        <Paper component="form" onSubmit={auditDownload('csv')} sx={{ p: 3 }}>
          <Typography variant="h6">Audit report</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Dates are inclusive, in UTC. At most 366 days and 10,000 rows per report.
          </Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={NO_WRAP_BUTTONS}>
            <TextField select label="Report" value={type} onChange={(e) => setType(e.target.value)} sx={{ minWidth: 240 }}>
              {AUDIT_TYPES.map((t) => <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>)}
            </TextField>
            <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <Button variant="contained" disabled={busy} onClick={auditDownload('csv')}>Download CSV</Button>
            <Button variant="outlined" disabled={busy} onClick={auditDownload('pdf')}>Download PDF</Button>
          </Stack>
        </Paper>
      )}

      <Paper sx={{ p: 3 }}>
        <Typography variant="h6">Product history</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          The tag’s current verdict, every reason and its full scan timeline.
        </Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={NO_WRAP_BUTTONS}>
          <TextField fullWidth label="Tag ID" value={tagId} onChange={(e) => setTagId(e.target.value)} />
          <Button variant="contained" disabled={busy || !tagId.trim()} onClick={tagDownload('csv')}>Download CSV</Button>
          <Button variant="outlined" disabled={busy || !tagId.trim()} onClick={tagDownload('pdf')}>Download PDF</Button>
        </Stack>
      </Paper>
    </Stack>
  )
}
