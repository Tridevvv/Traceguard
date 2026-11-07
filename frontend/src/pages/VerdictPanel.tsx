import { Alert, AlertTitle, Box, Paper, Stack, Typography } from '@mui/material'
import type { Verdict, VerdictReason, VerdictResult } from '../types'

const STYLE: Record<VerdictResult, { color: 'success' | 'warning' | 'error'; summary: string }> = {
  AUTHENTIC: { color: 'success', summary: 'Signatures, hash chain, route and timing all check out.' },
  SUSPECT: { color: 'warning', summary: 'The record is intact, but something needs a human to review it.' },
  COUNTERFEIT: { color: 'error', summary: 'Do not accept this item. The tag or its history failed verification.' },
}

/** Worst impact first, informational reasons (impact null) last. */
const RANK: Record<string, number> = { COUNTERFEIT: 0, SUSPECT: 1, AUTHENTIC: 2, null: 3 }

function sortReasons(reasons: VerdictReason[]): VerdictReason[] {
  return [...reasons].sort((a, b) => RANK[String(a.impact)] - RANK[String(b.impact)])
}

function severityOf(impact: VerdictResult | null): 'error' | 'warning' | 'info' {
  if (impact === 'COUNTERFEIT') return 'error'
  if (impact === 'SUSPECT') return 'warning'
  return 'info'
}

/** Shows the AUTHENTIC / SUSPECT / COUNTERFEIT verdict for one tag and every reason behind it. */
export function VerdictPanel({ verdict }: { verdict: Verdict }) {
  const style = STYLE[verdict.verdict]
  const reasons = sortReasons(verdict.reasons)

  return (
    <Paper sx={{ p: 3 }} aria-label="Verification result">
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
        <Box
          role="status"
          sx={{
            px: 2.5,
            py: 1,
            borderRadius: 2,
            bgcolor: `${style.color}.main`,
            color: `${style.color}.contrastText`,
            fontWeight: 800,
            fontSize: '1.4rem',
            letterSpacing: 1,
            textAlign: 'center',
          }}
        >
          {verdict.verdict}
        </Box>
        <Box>
          <Typography sx={{ fontWeight: 600 }}>{verdict.tagId}</Typography>
          <Typography color="text.secondary">{style.summary}</Typography>
          <Typography variant="caption" color="text.secondary">
            Checked {new Date(verdict.verifiedAt).toLocaleString()} · {verdict.timeline.length} scan(s) in history
          </Typography>
        </Box>
      </Stack>

      <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>
        {reasons.length === 0 ? 'No problems found' : `Reasons (${reasons.length})`}
      </Typography>
      <Stack spacing={1}>
        {reasons.map((r, i) => (
          <Alert key={`${r.code}-${r.eventSeq ?? 'x'}-${i}`} severity={severityOf(r.impact)}>
            <AlertTitle sx={{ fontFamily: 'monospace', mb: 0 }}>
              {r.code}
              {r.eventSeq !== null && ` · event #${r.eventSeq}`}
            </AlertTitle>
            {r.message}
          </Alert>
        ))}
      </Stack>
    </Paper>
  )
}
