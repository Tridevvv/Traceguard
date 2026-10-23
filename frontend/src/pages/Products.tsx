import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  Alert, Button, Chip, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'
import type { Batch, Product, TagsResponse } from '../types'
import { useAction } from '../useAction'

export function Products() {
  const { user } = useAuth()
  const [products, setProducts] = useState<Product[]>([])
  const [selected, setSelected] = useState<Product | null>(null)
  const [batches, setBatches] = useState<Batch[]>([])
  const [issued, setIssued] = useState<TagsResponse | null>(null)
  const { run: runList, error: listError } = useAction()

  const [name, setName] = useState('')
  const [productType, setProductType] = useState('')
  const [orgId, setOrgId] = useState('')
  const [label, setLabel] = useState('')
  const [count, setCount] = useState('5')
  const create = useAction()

  const loadProducts = useCallback(async () => {
    const data = await runList(() => api<Product[]>('/products'))
    if (data) setProducts(data)
  }, [runList])

  useEffect(() => {
    let cancelled = false
    void runList(() => api<Product[]>('/products')).then((data) => { if (data && !cancelled) setProducts(data) })
    return () => { cancelled = true }
  }, [runList])

  const loadBatches = async (p: Product) => {
    setSelected(p)
    setIssued(null)
    const data = await runList(() => api<Batch[]>(`/products/${p.id}/batches`))
    if (data) setBatches(data)
  }

  const addProduct = async (e: FormEvent) => {
    e.preventDefault()
    const body = { name, productType, organizationId: orgId ? Number(orgId) : undefined }
    const p = await create.run(() => api<Product>('/products', { method: 'POST', body }))
    if (p) { setName(''); setProductType(''); await loadProducts() }
  }

  const addBatch = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected) return
    const b = await create.run(() => api<Batch>(`/products/${selected.id}/batches`, { method: 'POST', body: { label } }))
    if (b) { setLabel(''); await loadBatches(selected) }
  }

  const issueTags = async (b: Batch) => {
    const res = await create.run(() => api<TagsResponse>(`/batches/${b.id}/tags?count=${Number(count) || 1}`, { method: 'POST' }))
    if (res) setIssued(res)
  }

  const recall = async (b: Batch) => {
    if (!selected || !window.confirm(`Recall batch ${b.label}? No new tags can be issued afterwards.`)) return
    const res = await create.run(() => api<Batch>(`/batches/${b.id}/recall`, { method: 'POST' }))
    if (res) await loadBatches(selected)
  }

  return (
    <Stack spacing={3}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Product Registry</Typography>
      {(listError || create.error) && <Alert severity="error">{listError ?? create.error}</Alert>}

      <Paper component="form" onSubmit={addProduct} sx={{ p: 3 }}>
        <Typography variant="h6" gutterBottom>New product</Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          <TextField label="Name" required value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Product type" required value={productType} onChange={(e) => setProductType(e.target.value)} helperText="e.g. pharma" />
          {user?.role === 'ADMIN' && (
            <TextField label="Organization ID" required type="number" value={orgId} onChange={(e) => setOrgId(e.target.value)} />
          )}
          <Button type="submit" variant="contained" disabled={create.busy}>Create</Button>
        </Stack>
      </Paper>

      <Paper sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow><TableCell>ID</TableCell><TableCell>Name</TableCell><TableCell>Type</TableCell><TableCell /></TableRow>
          </TableHead>
          <TableBody>
            {products.map((p) => (
              <TableRow key={p.id} selected={selected?.id === p.id}>
                <TableCell>{p.id}</TableCell><TableCell>{p.name}</TableCell><TableCell>{p.productType}</TableCell>
                <TableCell align="right"><Button size="small" onClick={() => void loadBatches(p)}>Batches</Button></TableCell>
              </TableRow>
            ))}
            {products.length === 0 && <TableRow><TableCell colSpan={4}>No products yet.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </Paper>

      {selected && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom>Batches of {selected.name}</Typography>
          <Stack component="form" onSubmit={addBatch} direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
            <TextField size="small" label="Batch label" required value={label} onChange={(e) => setLabel(e.target.value)} />
            <Button type="submit" variant="outlined" disabled={create.busy}>Add batch</Button>
            <TextField size="small" label="Tags to issue" type="number" value={count} onChange={(e) => setCount(e.target.value)} sx={{ width: 140 }} />
          </Stack>
          {batches.map((b) => (
            <Stack key={b.id} direction="row" spacing={1} sx={{ py: 0.5, alignItems: 'center' }}>
              <Typography sx={{ minWidth: 160 }}>#{b.id} {b.label}</Typography>
              {b.recalled && <Chip size="small" color="error" label="RECALLED" />}
              <Button size="small" disabled={b.recalled || create.busy} onClick={() => void issueTags(b)}>Issue tags</Button>
              <Button size="small" color="error" disabled={b.recalled || create.busy} onClick={() => void recall(b)}>Recall</Button>
            </Stack>
          ))}
          {batches.length === 0 && <Typography color="text.secondary">No batches yet.</Typography>}
          {issued && (
            <Alert severity="success" sx={{ mt: 2 }}>
              Issued {issued.tagIds.length} tag(s) for batch {issued.batchId}:
              <Typography component="pre" variant="body2" sx={{ m: 0, mt: 1, fontFamily: 'monospace' }}>{issued.tagIds.join('\n')}</Typography>
            </Alert>
          )}
        </Paper>
      )}
    </Stack>
  )
}
