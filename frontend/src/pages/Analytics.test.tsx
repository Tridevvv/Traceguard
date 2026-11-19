import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, renderAt, signInAs, stubFetch } from '../test/helpers'

const summary = {
  tagsIssued: 42,
  scansLast24Hours: 7,
  scansLast7Days: 31,
  openAlerts: 3,
  verdicts: { authentic: 5, suspect: 3, counterfeit: 2, unverified: 0 },
}

function backend() {
  return stubFetch((url) => {
    if (url === '/api/analytics/summary') return json(summary)
    if (url.startsWith('/api/analytics/scan-volume')) {
      return json([{ date: '2026-11-17', count: 2 }, { date: '2026-11-18', count: 5 }])
    }
    if (url.startsWith('/api/analytics/alerts')) {
      return json({ open: 3, byCode: [{ name: 'HASH_MISMATCH', count: 1 }, { name: 'DUPLICATE_PRESENCE', count: 2 }], bySeverity: [], daily: [] })
    }
    if (url.startsWith('/api/analytics/checkpoints')) {
      return json([{ checkpointId: 1, name: 'Factory', locationName: 'Boston, MA', scans: 9 }])
    }
    return json({}, 404)
  })
}

afterEach(() => vi.unstubAllGlobals())

describe('Analytics page', () => {
  it('shows the summary numbers and every chart', async () => {
    signInAs('ADMIN')
    backend()
    renderAt('/analytics')

    expect(await screen.findByText('42')).toBeInTheDocument()
    expect(screen.getByText('Tags issued')).toBeInTheDocument()
    expect(screen.getByText('Open alerts')).toBeInTheDocument()

    const verdicts = screen.getByRole('region', { name: 'Verdict distribution' })
    expect(within(verdicts).getByLabelText('COUNTERFEIT: 2')).toBeInTheDocument()
    // 5 of 10 verified tags are authentic; the legend line is split across elements, so match its full text.
    expect(within(verdicts).getByText((_, el) => el?.tagName === 'P' && el.textContent === 'AUTHENTIC 5 (50%)')).toBeInTheDocument()

    const scans = screen.getByRole('region', { name: 'Scan volume' })
    expect(within(scans).getByLabelText('2026-11-18: 5')).toBeInTheDocument()
    expect(within(scans).getByText(/Total 7/)).toBeInTheDocument()

    expect(screen.getByLabelText('Factory (Boston, MA): 9')).toBeInTheDocument()
  })

  it('lists alert reasons most frequent first', async () => {
    signInAs('AUDITOR')
    backend()
    renderAt('/analytics')

    const alerts = await screen.findByRole('region', { name: 'Alerts by reason' })
    const labels = within(alerts).getAllByText(/^(HASH_MISMATCH|DUPLICATE_PRESENCE)$/).map((e) => e.textContent)
    expect(labels).toEqual(['DUPLICATE_PRESENCE', 'HASH_MISMATCH'])
  })

  it('reloads the charts for a different period', async () => {
    signInAs('ADMIN')
    const fetchMock = backend()
    renderAt('/analytics')
    await screen.findByText('42')

    await userEvent.click(screen.getByLabelText('Period'))
    await userEvent.click(await screen.findByRole('option', { name: 'Last 7 days' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/analytics/scan-volume?days=7', expect.anything()))
  })

  it('tells a manufacturer the numbers cover only its own products', async () => {
    signInAs('MANUFACTURER')
    backend()
    renderAt('/analytics')
    expect(await screen.findByText(/organization’s products only/)).toBeInTheDocument()
  })
})
