import { useEffect, useState } from 'react'
import { Alert, Box, Button, GlobalStyles, Paper, Stack, Typography } from '@mui/material'
import { QRCodeSVG } from 'qrcode.react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { checkUrl } from '../qr'
import type { TagsResponse } from '../types'
import { useAction } from '../useAction'

/**
 * Printable QR labels for every tag in a batch (FR-3). Each code encodes the public check link for its tag,
 * so a consumer's phone camera opens the verdict directly. Printing hides the app chrome.
 */
export function QrLabels() {
  const { batchId } = useParams()
  const [tags, setTags] = useState<string[] | null>(null)
  const { run, error } = useAction()

  useEffect(() => {
    let cancelled = false
    void run(() => api<TagsResponse>(`/batches/${batchId}/tags`)).then((res) => {
      if (res && !cancelled) setTags(res.tagIds)
    })
    return () => { cancelled = true }
  }, [batchId, run])

  return (
    <Stack spacing={3}>
      <GlobalStyles styles={{ '@media print': { '.no-print': { display: 'none !important' }, body: { background: 'white' } } }} />
      <Stack className="no-print" direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700 }}>QR labels · batch #{batchId}</Typography>
          <Typography color="text.secondary">Scanning a label opens the public check page for that tag.</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button component={RouterLink} to="/products">Back</Button>
          <Button variant="contained" onClick={() => window.print()} disabled={!tags?.length}>Print labels</Button>
        </Stack>
      </Stack>
      {error && <Alert severity="error" className="no-print">{error}</Alert>}
      {tags && tags.length === 0 && <Alert severity="info">This batch has no tags yet. Issue tags in the Product Registry first.</Alert>}

      {tags && tags.length > 0 && (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))' }}>
          {tags.map((t) => (
            <Paper key={t} variant="outlined" sx={{ p: 2, textAlign: 'center', breakInside: 'avoid' }} aria-label={`QR label for ${t}`}>
              <QRCodeSVG value={checkUrl(t)} size={140} level="M" title={t} />
              <Typography sx={{ mt: 1, fontFamily: 'monospace', fontWeight: 600, fontSize: 13 }}>{t}</Typography>
              <Typography variant="caption" color="text.secondary">Scan to verify · TraceGuard</Typography>
            </Paper>
          ))}
        </Box>
      )}
    </Stack>
  )
}
