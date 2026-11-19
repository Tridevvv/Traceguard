import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fileNameFrom } from '../api/client'
import { auditReportPath, tagReportPath } from '../api/reports'
import { json, renderAt, signInAs, stubFetch } from '../test/helpers'

function fileResponse(name: string) {
  return new Response('a,b\n1,2', { status: 200, headers: { 'Content-Disposition': `attachment; filename="${name}"` } })
}

beforeEach(() => {
  // jsdom has no object URLs; the download helper only needs them to exist.
  URL.createObjectURL = vi.fn(() => 'blob:report')
  URL.revokeObjectURL = vi.fn()
})
afterEach(() => vi.unstubAllGlobals())

describe('report URLs', () => {
  it('builds audit and tag report paths', () => {
    expect(auditReportPath('alerts', 'pdf', '2026-11-01', '2026-11-18'))
      .toBe('/reports/audit?type=alerts&format=pdf&from=2026-11-01&to=2026-11-18')
    expect(auditReportPath('actions', 'csv', '', '')).toBe('/reports/audit?type=actions&format=csv')
    expect(tagReportPath('TG/1', 'csv')).toBe('/reports/tags/TG%2F1?format=csv')
  })

  it('reads the file name from Content-Disposition', () => {
    expect(fileNameFrom('attachment; filename="traceguard-alerts.csv"', 'x')).toBe('traceguard-alerts.csv')
    expect(fileNameFrom(null, 'fallback.pdf')).toBe('fallback.pdf')
  })
})

describe('Reports page', () => {
  it('downloads an audit report with the JWT and shows the file name', async () => {
    signInAs('AUDITOR')
    const fetchMock = stubFetch((url) => (url.startsWith('/api/reports/audit') ? fileResponse('traceguard-actions.csv') : json({}, 404)))
    renderAt('/reports')

    await userEvent.click(screen.getAllByRole('button', { name: 'Download CSV' })[0])

    expect(await screen.findByText('Downloaded traceguard-actions.csv')).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toMatch(/^\/api\/reports\/audit\?type=actions&format=csv&from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}$/)
    expect((init as RequestInit).headers).toHaveProperty('Authorization')
  })

  it('downloads a product history as PDF', async () => {
    signInAs('ADMIN')
    const fetchMock = stubFetch(() => fileResponse('traceguard-tag-TG-1.pdf'))
    renderAt('/reports')

    await userEvent.type(screen.getAllByLabelText('Tag ID')[0], 'TG-1')
    const pdfButtons = screen.getAllByRole('button', { name: 'Download PDF' })
    await userEvent.click(pdfButtons[pdfButtons.length - 1])

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/reports/tags/TG-1?format=pdf', expect.anything()))
    expect(await screen.findByText('Downloaded traceguard-tag-TG-1.pdf')).toBeInTheDocument()
  })

  it('shows a manufacturer only the product history export', async () => {
    signInAs('MANUFACTURER')
    stubFetch(() => json({}, 404))
    renderAt('/reports')

    expect(await screen.findByRole('heading', { name: 'Product history' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Audit report' })).not.toBeInTheDocument()
  })

  it('offers a history download on the Verify page to roles that may export it', async () => {
    signInAs('AUDITOR')
    const verdict = { tagId: 'TG-1', verdict: 'AUTHENTIC', reasons: [], timeline: [], verifiedAt: '2026-11-18T12:00:00Z' }
    const fetchMock = stubFetch((url) => {
      if (url === '/api/verify/TG-1') return json(verdict)
      if (url.startsWith('/api/reports/tags/')) return fileResponse('traceguard-tag-TG-1.csv')
      return json([])
    })
    renderAt('/verify/TG-1')

    await userEvent.click(await screen.findByRole('button', { name: 'Download history (CSV)' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/reports/tags/TG-1?format=csv', expect.anything()))
  })

  it('hides the Verify page history download from retailers', async () => {
    signInAs('RETAILER')
    stubFetch((url) => (url === '/api/verify/TG-1'
      ? json({ tagId: 'TG-1', verdict: 'AUTHENTIC', reasons: [], timeline: [], verifiedAt: '2026-11-18T12:00:00Z' })
      : json([])))
    renderAt('/verify/TG-1')

    expect(await screen.findByRole('status')).toHaveTextContent('AUTHENTIC')
    expect(screen.queryByRole('button', { name: /Download history/ })).not.toBeInTheDocument()
  })

  it('shows the server’s message when a download is refused', async () => {
    signInAs('MANUFACTURER')
    stubFetch(() => json({ message: 'Tag TG-X not found' }, 404))
    renderAt('/reports')

    await userEvent.type(screen.getByLabelText('Tag ID'), 'TG-X')
    await userEvent.click(screen.getByRole('button', { name: 'Download CSV' }))
    expect(await screen.findByText('Tag TG-X not found')).toBeInTheDocument()
  })
})
