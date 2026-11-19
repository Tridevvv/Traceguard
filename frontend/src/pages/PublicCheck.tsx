import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  Alert, Button, List, ListItem, ListItemText, Paper, Stack, TextField, Typography,
} from '@mui/material'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { tagFromScan } from '../qr'
import type { Verdict } from '../types'
import { useAction } from '../useAction'
import { VerdictPanel } from './VerdictPanel'

/** Minimal typing for the browser's built-in barcode reader (Chrome, Edge and Android; not in every browser). */
interface DetectedCode { rawValue: string }
interface BarcodeDetectorLike { detect(source: HTMLVideoElement): Promise<DetectedCode[]> }
type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike

function barcodeDetector(): BarcodeDetectorCtor | null {
  const ctor = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector
  return ctor && typeof navigator.mediaDevices?.getUserMedia === 'function' ? ctor : null
}

/**
 * Public consumer verification (FR-11): anyone can check a tag by ID or by scanning its QR label, with no account.
 * It calls GET /api/public/verify/{tagId}, which is rate limited on the server.
 */
export function PublicCheck() {
  const { tagId } = useParams()
  const navigate = useNavigate()
  const [input, setInput] = useState(tagId ?? '')
  const [result, setResult] = useState<Verdict | null>(null)
  const [scanning, setScanning] = useState(false)
  const [scanError, setScanError] = useState<string | null>(null)
  const { run, busy, error } = useAction()
  const videoRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    if (!tagId) return
    let cancelled = false
    void run(async () => {
      try {
        const v = await api<Verdict>(`/public/verify/${encodeURIComponent(tagId)}`)
        if (!cancelled) setResult(v)
      } catch (e) {
        if (e instanceof ApiError && e.status === 429) throw new Error('Too many checks from this network. Please wait a minute and try again.')
        throw e
      }
    })
    return () => { cancelled = true }
  }, [tagId, run])

  // Camera scanning: read frames until a QR code is found, then open its tag. Stops the camera on exit.
  useEffect(() => {
    const Detector = barcodeDetector()
    if (!scanning || !Detector) return
    let stream: MediaStream | null = null
    let stopped = false
    const detector = new Detector({ formats: ['qr_code'] })
    void (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        const video = videoRef.current
        if (!video || stopped) return
        video.srcObject = stream
        await video.play()
        while (!stopped) {
          const codes = await detector.detect(video).catch(() => [] as DetectedCode[])
          const found = codes.map((c) => tagFromScan(c.rawValue)).find((t): t is string => t !== null)
          if (found) {
            setScanning(false)
            navigate(`/check/${encodeURIComponent(found)}`)
            return
          }
          await new Promise((r) => setTimeout(r, 300))
        }
      } catch {
        setScanError('Could not open the camera. Allow camera access, or type the tag ID instead.')
        setScanning(false)
      }
    })()
    return () => {
      stopped = true
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [scanning, navigate])

  const current = result && result.tagId === tagId ? result : null

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const id = tagFromScan(input)
    if (id) navigate(`/check/${encodeURIComponent(id)}`)
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 820, mx: 'auto' }}>
      <Typography variant="h4" sx={{ fontWeight: 700 }}>Check a product</Typography>
      <Typography color="text.secondary">
        Enter the tag ID printed on the label, or scan its QR code. No account is needed.
      </Typography>
      <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField fullWidth label="Tag ID" value={input} onChange={(e) => setInput(e.target.value)} />
          <Button type="submit" variant="contained" disabled={!input.trim() || busy}>
            {busy ? 'Checking…' : 'Check'}
          </Button>
          {barcodeDetector() && (
            <Button variant="outlined" onClick={() => { setScanError(null); setScanning((s) => !s) }}>
              {scanning ? 'Stop camera' : 'Scan QR'}
            </Button>
          )}
        </Stack>
        {scanning && (
          <video ref={videoRef} muted playsInline aria-label="Camera preview" style={{ width: '100%', maxWidth: 420, marginTop: 16, borderRadius: 8 }} />
        )}
        {!barcodeDetector() && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            Tip: point your phone’s camera at the QR label; it opens this page for that tag.
          </Typography>
        )}
      </Paper>

      {scanError && <Alert severity="warning">{scanError}</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {current && <VerdictPanel verdict={current} />}
      {current && current.timeline.length > 0 && (
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6">Journey</Typography>
          <List dense>
            {current.timeline.map((t) => (
              <ListItem key={t.seq} disableGutters>
                <ListItemText
                  primary={`${t.eventType} at ${t.checkpointName}, ${t.locationName}`}
                  secondary={new Date(t.occurredAt).toLocaleString()}
                />
              </ListItem>
            ))}
          </List>
        </Paper>
      )}
    </Stack>
  )
}
