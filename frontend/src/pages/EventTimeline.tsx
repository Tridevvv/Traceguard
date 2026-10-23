import { Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { Checkpoint, ScanEventView } from '../types'

export function EventTimeline({ events, checkpoints }: { events: ScanEventView[]; checkpoints: Checkpoint[] }) {
  const nameOf = (id: number) => checkpoints.find((c) => c.id === id)?.name ?? `Checkpoint ${id}`
  return (
    <Paper sx={{ overflowX: 'auto' }}>
      <Typography variant="h6" sx={{ p: 2, pb: 0 }}>Ledger timeline</Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>#</TableCell><TableCell>Event</TableCell><TableCell>Checkpoint</TableCell>
            <TableCell>Time</TableCell><TableCell>Hash</TableCell><TableCell>Previous hash</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {events.map((e) => (
            <TableRow key={e.id}>
              <TableCell>{e.seq}</TableCell>
              <TableCell>{e.eventType}</TableCell>
              <TableCell>{nameOf(e.checkpointId)}</TableCell>
              <TableCell>{new Date(e.occurredAt).toLocaleString()}</TableCell>
              <TableCell sx={{ fontFamily: 'monospace' }}>{e.hash.slice(0, 10)}…</TableCell>
              <TableCell sx={{ fontFamily: 'monospace' }}>{e.prevHash.slice(0, 10)}…</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Paper>
  )
}
