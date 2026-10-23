import { useEffect, useState, type FormEvent } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { api } from '../api/client'
import type { Checkpoint, EventType, ScanEventView } from '../types'
import { useAction } from '../useAction'
import { EventTimeline } from './EventTimeline'

const EVENT_TYPES: EventType[] = ['REGISTERED', 'RECEIVED', 'IN_TRANSIT', 'DELIVERED']

export function ScanConsole() {
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [checkpointId, setCheckpointId] = useState('')
  const [tagId, setTagId] = useState('')
  const [eventType, setEventType] = useState<EventType>('RECEIVED')
  const [events, setEvents] = useState<ScanEventView[]>([])
  const [lastSeq, setLastSeq] = useState<number | null>(null)
  const { run: runLoad, error: loadError } = useAction()
  const scan = useAction()

  useEffect(() => {
    void runLoad(() => api<Checkpoint[]>('/checkpoints')).then((d) => d && setCheckpoints(d))
  }, [runLoad])

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const id = tagId.trim()
    const created = await scan.run(() =>
      api<ScanEventView>('/scans/simulate', { method: 'POST', body: { tagId: id, checkpointId: Number(checkpointId), eventType } }))
    if (!created) return
    setLastSeq(created.seq)
    const all = await scan.run(() => api<ScanEventView[]>(`/tags/${encodeURIComponent(id)}/events`))
    if (all) setEvents(all)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Scan Console</Typography>
      <Typography color="text.secondary">Simulates an RFID reader at a checkpoint. Each scan becomes a signed event linked to the previous one.</Typography>
      {(loadError || scan.error) && <Alert severity="error">{loadError ?? scan.error}</Alert>}
      {lastSeq !== null && !scan.error && <Alert severity="success">Scan recorded as event #{lastSeq}.</Alert>}

      <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Tag ID" required value={tagId} onChange={(e) => setTagId(e.target.value)} sx={{ flex: 1 }} />
          <TextField select label="Checkpoint" required value={checkpointId} onChange={(e) => setCheckpointId(e.target.value)} sx={{ minWidth: 220 }}>
            {checkpoints.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name} ({c.locationName})</MenuItem>)}
          </TextField>
          <TextField select label="Event" value={eventType} onChange={(e) => setEventType(e.target.value as EventType)} sx={{ minWidth: 160 }}>
            {EVENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </TextField>
          <Button type="submit" variant="contained" disabled={scan.busy || !checkpointId}>Scan</Button>
        </Stack>
      </Paper>

      {events.length > 0 && <EventTimeline events={events} checkpoints={checkpoints} />}
    </Stack>
  )
}
