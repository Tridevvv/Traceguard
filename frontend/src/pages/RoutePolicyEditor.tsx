import { useEffect, useState, type FormEvent } from 'react'
import {
  Alert, Button, IconButton, List, ListItem, ListItemText, MenuItem, Paper, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { api } from '../api/client'
import type { Checkpoint, RoutePolicy } from '../types'
import { useAction } from '../useAction'

/** Form state for one stop: strings, so empty fields stay empty while typing. */
interface StepDraft {
  checkpointId: string
  maxHours: string
}

const EMPTY_STEP: StepDraft = { checkpointId: '', maxHours: '' }

/**
 * Create or edit the approved route for a product type: the checkpoints in order, an optional time limit
 * for each leg and a limit for the whole journey. The verification engine checks every scan against it.
 */
export function RoutePolicyEditor({ checkpoints }: { checkpoints: Checkpoint[] }) {
  const [policies, setPolicies] = useState<RoutePolicy[]>([])
  const [editingId, setEditingId] = useState<number | null>(null)
  const [productType, setProductType] = useState('')
  const [maxTotalHours, setMaxTotalHours] = useState('168')
  const [steps, setSteps] = useState<StepDraft[]>([{ ...EMPTY_STEP }, { ...EMPTY_STEP }])
  const [message, setMessage] = useState<string | null>(null)
  const { run, busy, error } = useAction()

  useEffect(() => {
    let cancelled = false
    void run(() => api<RoutePolicy[]>('/route-policies')).then((p) => {
      if (p && !cancelled) setPolicies(p)
    })
    return () => { cancelled = true }
  }, [run])

  const nameOf = (id: number) => checkpoints.find((c) => c.id === id)?.name ?? `Checkpoint ${id}`

  const reset = () => {
    setEditingId(null)
    setProductType('')
    setMaxTotalHours('168')
    setSteps([{ ...EMPTY_STEP }, { ...EMPTY_STEP }])
  }

  const edit = (p: RoutePolicy) => {
    setEditingId(p.id)
    setProductType(p.productType)
    setMaxTotalHours(String(p.maxTotalHours))
    setSteps(p.steps.map((s) => ({ checkpointId: String(s.checkpointId), maxHours: s.maxHoursFromPrevious?.toString() ?? '' })))
    setMessage(null)
  }

  const updateStep = (i: number, change: Partial<StepDraft>) =>
    setSteps((all) => all.map((s, j) => (j === i ? { ...s, ...change } : s)))

  const move = (i: number, by: -1 | 1) =>
    setSteps((all) => {
      const next = [...all]
      const moved = next[i]
      next[i] = next[i + by]
      next[i + by] = moved
      return next
    })

  const save = async (e: FormEvent) => {
    e.preventDefault()
    const body = {
      productType: productType.trim(),
      maxTotalHours: Number(maxTotalHours),
      // The first stop has no previous stop, so it never has a leg limit.
      steps: steps.map((s, i) => ({
        checkpointId: Number(s.checkpointId),
        maxHoursFromPrevious: i > 0 && s.maxHours ? Number(s.maxHours) : null,
      })),
    }
    const saved = editingId === null
      ? await run(() => api<RoutePolicy>('/route-policies', { method: 'POST', body }))
      : await run(() => api<RoutePolicy>(`/route-policies/${editingId}`, { method: 'PUT', body }))
    if (!saved) return
    setPolicies((all) => [...all.filter((p) => p.id !== saved.id), saved])
    setMessage(`Route for "${saved.productType}" saved with ${saved.steps.length} stop(s).`)
    reset()
  }

  const complete = productType.trim() !== '' && steps.every((s) => s.checkpointId !== '')

  return (
    <Paper component="form" onSubmit={save} sx={{ p: 3 }} aria-label="Route policies">
      <Typography variant="h6" gutterBottom>Route policies</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        The approved order of checkpoints for a product type. Scans out of order, at other checkpoints or slower than
        the limits make a tag SUSPECT.
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {message && !error && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setMessage(null)}>{message}</Alert>}

      {policies.length > 0 && (
        <List dense sx={{ mb: 2 }}>
          {policies.map((p) => (
            <ListItem key={p.id} secondaryAction={<Button size="small" onClick={() => edit(p)}>Edit</Button>}>
              <ListItemText
                primary={`${p.productType} (max ${p.maxTotalHours} h)`}
                secondary={p.steps.map((s) => nameOf(s.checkpointId)).join(' → ')}
              />
            </ListItem>
          ))}
        </List>
      )}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField label="Product type" required value={productType} disabled={editingId !== null}
          onChange={(e) => setProductType(e.target.value)} helperText="Must match the product's type, e.g. pharma" />
        <TextField label="Max total hours" required type="number" value={maxTotalHours}
          onChange={(e) => setMaxTotalHours(e.target.value)} slotProps={{ htmlInput: { min: 1 } }} />
      </Stack>

      <Stack spacing={1.5}>
        {steps.map((s, i) => (
          <Stack key={i} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: { sm: 'center' } }}>
            <Typography sx={{ width: 60 }}>Stop {i + 1}</Typography>
            <TextField select label="Checkpoint" required value={s.checkpointId} sx={{ minWidth: 220 }}
              onChange={(e) => updateStep(i, { checkpointId: e.target.value })}>
              {checkpoints.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.name} ({c.locationName})</MenuItem>)}
            </TextField>
            {i > 0 && (
              <TextField label="Max hours from previous" type="number" value={s.maxHours} sx={{ width: 200 }}
                onChange={(e) => updateStep(i, { maxHours: e.target.value })} slotProps={{ htmlInput: { min: 1 } }} />
            )}
            <Tooltip title="Move up"><span>
              <IconButton aria-label={`Move stop ${i + 1} up`} disabled={i === 0} onClick={() => move(i, -1)}>↑</IconButton>
            </span></Tooltip>
            <Tooltip title="Move down"><span>
              <IconButton aria-label={`Move stop ${i + 1} down`} disabled={i === steps.length - 1} onClick={() => move(i, 1)}>↓</IconButton>
            </span></Tooltip>
            <Tooltip title="Remove stop"><span>
              <IconButton aria-label={`Remove stop ${i + 1}`} disabled={steps.length === 1}
                onClick={() => setSteps((all) => all.filter((_, j) => j !== i))}>✕</IconButton>
            </span></Tooltip>
          </Stack>
        ))}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
        <Button onClick={() => setSteps((all) => [...all, { ...EMPTY_STEP }])}>Add stop</Button>
        <Button type="submit" variant="contained" disabled={busy || !complete}>
          {editingId === null ? 'Create route' : 'Save changes'}
        </Button>
        {editingId !== null && <Button onClick={reset}>Cancel</Button>}
      </Stack>
    </Paper>
  )
}
