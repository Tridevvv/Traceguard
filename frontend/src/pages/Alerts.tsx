import { useEffect, useState } from 'react'
import {
  Alert, Button, Chip, Link, Paper, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { AlertItem, AlertSeverity, AlertStatus } from '../types'
import { useAction } from '../useAction'
import { sortAlerts } from './alertOrder'

type Filter = AlertStatus | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'ALL', label: 'All' },
]

const SEVERITY_COLOR: Record<AlertSeverity, 'error' | 'warning' | 'default'> = {
  HIGH: 'error',
  MEDIUM: 'warning',
  LOW: 'default',
}

/**
 * Alerts center (FR-9). Lists the alerts the signed-in user may see (the backend scopes a manufacturer to its own
 * products) and lets admins and manufacturers move an alert from OPEN to ACKNOWLEDGED to RESOLVED.
 * Auditors get a read-only view.
 */
export function Alerts() {
  const { user } = useAuth()
  const [filter, setFilter] = useState<Filter>('OPEN')
  const [alerts, setAlerts] = useState<AlertItem[]>([])
  // Bumped after a status change so the list is fetched again.
  const [version, setVersion] = useState(0)
  const { run: runList, busy: listBusy, error: listError } = useAction()
  const change = useAction()
  const canManage = user?.role === 'ADMIN' || user?.role === 'MANUFACTURER'

  useEffect(() => {
    let cancelled = false
    const query = filter === 'ALL' ? '' : `?status=${filter}`
    void runList(() => api<AlertItem[]>(`/alerts${query}`)).then((data) => {
      if (data && !cancelled) setAlerts(sortAlerts(data))
    })
    return () => { cancelled = true }
  }, [filter, version, runList])

  const setStatus = async (a: AlertItem, status: AlertStatus) => {
    const updated = await change.run(() => api<AlertItem>(`/alerts/${a.id}`, { method: 'PATCH', body: { status } }))
    if (updated) setVersion((v) => v + 1)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Alerts</Typography>
      <Typography color="text.secondary">
        Every SUSPECT or COUNTERFEIT finding raises one alert per reason. High-severity alerts are also emailed when
        SMTP is configured.{!canManage && ' Your role has read-only access.'}
      </Typography>
      {(listError || change.error) && <Alert severity="error">{listError ?? change.error}</Alert>}

      <Paper>
        <Tabs value={filter} onChange={(_, v: Filter) => setFilter(v)} aria-label="Alert status filter">
          {FILTERS.map((f) => <Tab key={f.value} value={f.value} label={f.label} />)}
        </Tabs>
        <Table size="small" sx={{ overflowX: 'auto' }}>
          <TableHead>
            <TableRow>
              <TableCell>Severity</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell>Tag</TableCell>
              <TableCell>Message</TableCell>
              <TableCell>Raised</TableCell>
              <TableCell>Status</TableCell>
              {canManage && <TableCell align="right">Actions</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {alerts.map((a) => (
              <TableRow key={a.id}>
                <TableCell><Chip size="small" label={a.severity} color={SEVERITY_COLOR[a.severity]} /></TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{a.code}</TableCell>
                <TableCell>
                  <Link component={RouterLink} to={`/verify/${encodeURIComponent(a.tagId)}`}>{a.tagId}</Link>
                </TableCell>
                <TableCell>{a.message}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(a.createdAt).toLocaleString()}</TableCell>
                <TableCell>{a.status}</TableCell>
                {canManage && (
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {a.status === 'OPEN' && (
                      <Button size="small" disabled={change.busy} onClick={() => void setStatus(a, 'ACKNOWLEDGED')}>
                        Acknowledge
                      </Button>
                    )}
                    {a.status !== 'RESOLVED' && (
                      <Button size="small" color="success" disabled={change.busy} onClick={() => void setStatus(a, 'RESOLVED')}>
                        Resolve
                      </Button>
                    )}
                  </TableCell>
                )}
              </TableRow>
            ))}
            {alerts.length === 0 && !listBusy && (
              <TableRow>
                <TableCell colSpan={canManage ? 7 : 6}>No {filter === 'ALL' ? '' : filter.toLowerCase() + ' '}alerts.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
    </Stack>
  )
}
