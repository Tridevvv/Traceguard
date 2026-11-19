import { Box, Paper, Stack, Tooltip, Typography } from '@mui/material'
import type { ReactNode } from 'react'

/**
 * Small, dependency-free charts for the analytics page, drawn with plain boxes.
 * Every bar carries a text label (and an aria-label), so the numbers stay readable without hovering.
 */

export function ChartCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Paper sx={{ p: 3 }} aria-label={title} role="region">
      <Typography variant="h6">{title}</Typography>
      {subtitle && <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{subtitle}</Typography>}
      {children}
    </Paper>
  )
}

export function StatCard({ label, value, color = 'text.primary' }: { label: string; value: number; color?: string }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="h4" sx={{ fontWeight: 700, color }}>{value.toLocaleString()}</Typography>
      <Typography variant="body2" color="text.secondary">{label}</Typography>
    </Paper>
  )
}

export interface BarItem {
  label: string
  value: number
  color?: string
}

/** Horizontal bars in the order given. mono shows labels in monospace (for reason codes). */
export function BarList({ items, empty = 'No data in this period.', mono = false }: { items: BarItem[]; empty?: string; mono?: boolean }) {
  const max = Math.max(1, ...items.map((i) => i.value))
  if (items.length === 0 || items.every((i) => i.value === 0)) {
    return <Typography color="text.secondary">{empty}</Typography>
  }
  return (
    <Stack spacing={1.25}>
      {items.map((i) => (
        <Box key={i.label} aria-label={`${i.label}: ${i.value}`}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', mb: 0.25 }}>
            <Typography variant="body2" sx={{ fontFamily: mono ? 'monospace' : undefined }}>
              {i.label}
            </Typography>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>{i.value}</Typography>
          </Stack>
          <Box sx={{ height: 10, bgcolor: 'action.hover', borderRadius: 1 }}>
            <Box sx={{ height: '100%', width: `${(i.value / max) * 100}%`, bgcolor: i.color ?? 'primary.main', borderRadius: 1 }} />
          </Box>
        </Box>
      ))}
    </Stack>
  )
}

/** One column per day, oldest on the left. Hover shows the date and count. */
export function DailyColumns({ days, color = 'primary.main' }: { days: { date: string; count: number }[]; color?: string }) {
  const max = Math.max(1, ...days.map((d) => d.count))
  const total = days.reduce((s, d) => s + d.count, 0)
  return (
    <Box>
      <Stack direction="row" spacing={0.25} sx={{ height: 140, alignItems: 'flex-end' }}>
        {days.map((d) => (
          <Tooltip key={d.date} title={`${d.date}: ${d.count}`}>
            <Box
              aria-label={`${d.date}: ${d.count}`}
              sx={{ flex: 1, minWidth: 2, height: `${Math.max(d.count ? 4 : 1, (d.count / max) * 100)}%`, bgcolor: d.count ? color : 'action.hover', borderRadius: '2px 2px 0 0' }}
            />
          </Tooltip>
        ))}
      </Stack>
      <Stack direction="row" sx={{ justifyContent: 'space-between', mt: 0.5 }}>
        <Typography variant="caption" color="text.secondary">{days[0]?.date}</Typography>
        <Typography variant="caption" color="text.secondary">Total {total} · peak {days.length ? Math.max(...days.map((d) => d.count)) : 0}</Typography>
        <Typography variant="caption" color="text.secondary">{days[days.length - 1]?.date}</Typography>
      </Stack>
    </Box>
  )
}

/** A single 100% bar split into colored segments, with a legend underneath. */
export function StackedBar({ items }: { items: BarItem[] }) {
  const total = items.reduce((s, i) => s + i.value, 0)
  if (total === 0) return <Typography color="text.secondary">No tags verified yet.</Typography>
  return (
    <Box>
      <Stack direction="row" sx={{ height: 28, borderRadius: 1, overflow: 'hidden' }}>
        {items.filter((i) => i.value > 0).map((i) => (
          <Box key={i.label} aria-label={`${i.label}: ${i.value}`} sx={{ width: `${(i.value / total) * 100}%`, bgcolor: i.color }} />
        ))}
      </Stack>
      <Stack direction="row" spacing={2} sx={{ mt: 1.5, flexWrap: 'wrap' }} useFlexGap>
        {items.map((i) => (
          <Stack key={i.label} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
            <Box sx={{ width: 12, height: 12, borderRadius: '3px', bgcolor: i.color }} />
            <Typography variant="body2">
              {i.label} <b>{i.value}</b> ({Math.round((i.value / total) * 100)}%)
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  )
}
