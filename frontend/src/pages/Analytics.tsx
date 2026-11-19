import { useEffect, useState } from 'react'
import { Alert, Box, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { AlertsReport, AnalyticsSummary, CheckpointActivity, DailyCount } from '../types'
import { useAction } from '../useAction'
import { BarList, ChartCard, DailyColumns, StackedBar, StatCard } from './Charts'

const PERIODS = [7, 30, 90]

interface Data {
  days: number
  summary: AnalyticsSummary
  scans: DailyCount[]
  alerts: AlertsReport
  checkpoints: CheckpointActivity[]
}

/**
 * Analytics dashboard (FR-12): scan volume, verdict distribution, alerts by type and checkpoint activity.
 * The backend scopes the numbers: admins and auditors see everything, a manufacturer only its own tags.
 */
export function Analytics() {
  const { user } = useAuth()
  const [days, setDays] = useState(30)
  const [data, setData] = useState<Data | null>(null)
  const { run, error } = useAction()

  useEffect(() => {
    let cancelled = false
    void run(async () => {
      const [summary, scans, alerts, checkpoints] = await Promise.all([
        api<AnalyticsSummary>('/analytics/summary'),
        api<DailyCount[]>(`/analytics/scan-volume?days=${days}`),
        api<AlertsReport>(`/analytics/alerts?days=${days}`),
        api<CheckpointActivity[]>(`/analytics/checkpoints?days=${days}`),
      ])
      if (!cancelled) setData({ days, summary, scans, alerts, checkpoints })
    })
    return () => { cancelled = true }
  }, [days, run])

  const v = data?.summary.verdicts

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>Analytics</Typography>
          <Typography color="text.secondary">
            {user?.role === 'MANUFACTURER' ? 'Your organization’s products only.' : 'All products in the system.'}
          </Typography>
        </Box>
        <TextField select size="small" label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))} sx={{ width: 160 }}>
          {PERIODS.map((p) => <MenuItem key={p} value={p}>Last {p} days</MenuItem>)}
        </TextField>
      </Stack>
      {error && <Alert severity="error">{error}</Alert>}

      {data && v && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' } }}>
            <StatCard label="Tags issued" value={data.summary.tagsIssued} />
            <StatCard label="Scans, last 24 hours" value={data.summary.scansLast24Hours} />
            <StatCard label="Scans, last 7 days" value={data.summary.scansLast7Days} />
            <StatCard label="Open alerts" value={data.summary.openAlerts} color={data.summary.openAlerts ? 'error.main' : undefined} />
          </Box>

          <ChartCard title="Verdict distribution" subtitle="Latest verdict of every tag that has been verified.">
            <StackedBar items={[
              { label: 'AUTHENTIC', value: v.authentic, color: 'success.main' },
              { label: 'SUSPECT', value: v.suspect, color: 'warning.main' },
              { label: 'COUNTERFEIT', value: v.counterfeit, color: 'error.main' },
              { label: 'Not yet verified', value: v.unverified, color: 'grey.400' },
            ]} />
          </ChartCard>

          <ChartCard title="Scan volume" subtitle={`Scans per day, last ${data.days} days (UTC).`}>
            <DailyColumns days={data.scans} />
          </ChartCard>

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
            <ChartCard title="Alerts by reason" subtitle={`${data.alerts.open} open now · raised in the last ${data.days} days`}>
              <BarList
                items={[...data.alerts.byCode].sort((a, b) => b.count - a.count).map((c) => ({ label: c.name, value: c.count, color: 'error.main' }))}
                empty="No alerts in this period."
                mono
              />
            </ChartCard>
            <ChartCard title="Checkpoint activity" subtitle={`Scans per checkpoint, last ${data.days} days`}>
              <BarList
                items={[...data.checkpoints].sort((a, b) => b.scans - a.scans).map((c) => ({ label: `${c.name} (${c.locationName})`, value: c.scans }))}
                empty="No scans in this period."
              />
            </ChartCard>
          </Box>
        </>
      )}
    </Stack>
  )
}
