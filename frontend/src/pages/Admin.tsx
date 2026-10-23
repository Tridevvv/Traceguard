import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { api } from '../api/client'
import type { Checkpoint, Organization, OrgType, Role } from '../types'
import { useAction } from '../useAction'

const ORG_TYPES: OrgType[] = ['MANUFACTURER', 'DISTRIBUTOR', 'RETAILER', 'OTHER']
const ROLES: Role[] = ['ADMIN', 'MANUFACTURER', 'DISTRIBUTOR', 'RETAILER', 'AUDITOR']

export function Admin() {
  const [orgs, setOrgs] = useState<Organization[]>([])
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const act = useAction()
  const runAct = act.run

  const [orgName, setOrgName] = useState('')
  const [orgType, setOrgType] = useState<OrgType>('MANUFACTURER')

  const [cpOrg, setCpOrg] = useState('')
  const [cpName, setCpName] = useState('')
  const [cpLocation, setCpLocation] = useState('')
  const [cpLat, setCpLat] = useState('')
  const [cpLon, setCpLon] = useState('')

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<Role>('MANUFACTURER')
  const [userOrg, setUserOrg] = useState('')

  const reload = useCallback(async () => {
    const [o, c] = await Promise.all([
      runAct(() => api<Organization[]>('/organizations')),
      runAct(() => api<Checkpoint[]>('/checkpoints')),
    ])
    if (o) setOrgs(o)
    if (c) setCheckpoints(c)
  }, [runAct])

  useEffect(() => {
    let cancelled = false
    void Promise.all([
      runAct(() => api<Organization[]>('/organizations')),
      runAct(() => api<Checkpoint[]>('/checkpoints')),
    ]).then(([o, c]) => {
      if (cancelled) return
      if (o) setOrgs(o)
      if (c) setCheckpoints(c)
    })
    return () => { cancelled = true }
  }, [runAct])

  const addOrg = async (e: FormEvent) => {
    e.preventDefault()
    const r = await act.run(() => api<Organization>('/organizations', { method: 'POST', body: { name: orgName, type: orgType } }))
    if (r) { setMessage(`Organization "${r.name}" created (ID ${r.id}).`); setOrgName(''); await reload() }
  }

  const addCheckpoint = async (e: FormEvent) => {
    e.preventDefault()
    const body = { organizationId: Number(cpOrg), name: cpName, locationName: cpLocation, latitude: Number(cpLat), longitude: Number(cpLon) }
    const r = await act.run(() => api<Checkpoint>('/checkpoints', { method: 'POST', body }))
    if (r) { setMessage(`Checkpoint "${r.name}" created with its own signing key (ID ${r.id}).`); setCpName(''); setCpLocation(''); await reload() }
  }

  const addUser = async (e: FormEvent) => {
    e.preventDefault()
    const body = { email, password, role, organizationId: userOrg ? Number(userOrg) : null }
    const r = await act.run(() => api<{ email: string }>('/auth/register', { method: 'POST', body }))
    if (r) { setMessage(`User ${r.email} created.`); setEmail(''); setPassword('') }
  }

  const orgItems = orgs.map((o) => <MenuItem key={o.id} value={String(o.id)}>{o.name} (ID {o.id})</MenuItem>)

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Admin Panel</Typography>
      {act.error && <Alert severity="error">{act.error}</Alert>}
      {message && !act.error && <Alert severity="success" onClose={() => setMessage(null)}>{message}</Alert>}

      <Paper component="form" onSubmit={addOrg} sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>New organization</Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Name" required value={orgName} onChange={(e) => setOrgName(e.target.value)} sx={{ flex: 1 }} />
          <TextField select label="Type" value={orgType} onChange={(e) => setOrgType(e.target.value as OrgType)} sx={{ minWidth: 180 }}>
            {ORG_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </TextField>
          <Button type="submit" variant="contained" disabled={act.busy}>Create</Button>
        </Stack>
      </Paper>

      <Paper component="form" onSubmit={addCheckpoint} sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>New checkpoint</Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} useFlexGap sx={{ flexWrap: 'wrap' }}>
          <TextField select label="Organization" required value={cpOrg} onChange={(e) => setCpOrg(e.target.value)} sx={{ minWidth: 220 }}>{orgItems}</TextField>
          <TextField label="Name" required value={cpName} onChange={(e) => setCpName(e.target.value)} />
          <TextField label="Location" required value={cpLocation} onChange={(e) => setCpLocation(e.target.value)} />
          <TextField label="Latitude" required type="number" value={cpLat} onChange={(e) => setCpLat(e.target.value)} slotProps={{ htmlInput: { step: 'any' } }} />
          <TextField label="Longitude" required type="number" value={cpLon} onChange={(e) => setCpLon(e.target.value)} slotProps={{ htmlInput: { step: 'any' } }} />
          <Button type="submit" variant="contained" disabled={act.busy}>Create</Button>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          {checkpoints.length} checkpoint(s) exist. Latitude and longitude are used later for impossible-travel detection.
        </Typography>
      </Paper>

      <Paper component="form" onSubmit={addUser} sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>New user</Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} useFlexGap sx={{ flexWrap: 'wrap' }}>
          <TextField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <TextField label="Password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} helperText="At least 8 characters" slotProps={{ htmlInput: { minLength: 8 } }} />
          <TextField select label="Role" value={role} onChange={(e) => setRole(e.target.value as Role)} sx={{ minWidth: 170 }}>
            {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
          </TextField>
          <TextField select label="Organization (optional)" value={userOrg} onChange={(e) => setUserOrg(e.target.value)} sx={{ minWidth: 220 }}>
            <MenuItem value="">None</MenuItem>{orgItems}
          </TextField>
          <Button type="submit" variant="contained" disabled={act.busy}>Create</Button>
        </Stack>
      </Paper>
    </Stack>
  )
}
