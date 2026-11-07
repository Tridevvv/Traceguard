import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../auth/AuthProvider'
import { tokenStore } from '../api/client'
import type { Verdict } from '../types'

function fakeJwt(role: string): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
  return `${b64({ alg: 'HS256' })}.${b64({ sub: 'user@test.local', role, exp: Math.floor(Date.now() / 1000) + 3600 })}.sig`
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status })
}

function verdict(overrides: Partial<Verdict>): Verdict {
  return {
    tagId: 'TG-TEST',
    verdict: 'AUTHENTIC',
    reasons: [],
    timeline: [
      { seq: 0, eventType: 'REGISTERED', checkpointName: 'Factory', locationName: 'Boston', occurredAt: '2026-11-01T10:00:00Z' },
    ],
    verifiedAt: '2026-11-06T12:00:00Z',
    ...overrides,
  }
}

/** Fake backend: /verify returns the given verdict, the timeline endpoints return one event and one checkpoint. */
function stubBackend(v: Verdict | Response) {
  const fetchMock = vi.fn(async (url: string) => {
    if (url.startsWith('/api/verify/')) return v instanceof Response ? v : json(v)
    if (url.endsWith('/events')) {
      return json([{ id: 1, tagId: 'TG-TEST', checkpointId: 7, eventType: 'REGISTERED', occurredAt: '2026-11-01T10:00:00Z', seq: 0, prevHash: '0'.repeat(64), hash: 'ab'.repeat(32) }])
    }
    if (url === '/api/checkpoints') return json([{ id: 7, organizationId: 1, name: 'Factory', locationName: 'Boston', latitude: 0, longitude: 0 }])
    return json({}, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('Verify page verdict view', () => {
  it('shows AUTHENTIC with no problems and the ledger timeline', async () => {
    tokenStore.set(fakeJwt('RETAILER'))
    const fetchMock = stubBackend(verdict({}))
    renderAt('/verify/TG-TEST')

    expect(await screen.findByRole('status')).toHaveTextContent('AUTHENTIC')
    expect(screen.getByText('No problems found')).toBeInTheDocument()
    expect(await screen.findByText('Ledger timeline')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/verify/TG-TEST', expect.anything())
  })

  it('shows COUNTERFEIT with every reason, worst first', async () => {
    tokenStore.set(fakeJwt('AUDITOR'))
    stubBackend(verdict({
      verdict: 'COUNTERFEIT',
      reasons: [
        { code: 'NO_ROUTE_POLICY', impact: null, message: 'No route policy for this product.', eventSeq: null },
        { code: 'ROUTE_DEVIATION', impact: 'SUSPECT', message: 'Skipped the distributor.', eventSeq: 2 },
        { code: 'DUPLICATE_PRESENCE', impact: 'COUNTERFEIT', message: 'Seen in Boston and Chicago 40 seconds apart.', eventSeq: 3 },
      ],
    }))
    renderAt('/verify/TG-TEST')

    expect(await screen.findByRole('status')).toHaveTextContent('COUNTERFEIT')
    expect(screen.getByText('Reasons (3)')).toBeInTheDocument()

    const alerts = within(screen.getByLabelText('Verification result')).getAllByRole('alert')
    expect(alerts.map((a) => a.textContent)).toEqual([
      expect.stringContaining('DUPLICATE_PRESENCE · event #3'),
      expect.stringContaining('ROUTE_DEVIATION · event #2'),
      expect.stringContaining('NO_ROUTE_POLICY'),
    ])
    expect(screen.getByText('Seen in Boston and Chicago 40 seconds apart.')).toBeInTheDocument()
  })

  it('shows SUSPECT for a recalled batch', async () => {
    tokenStore.set(fakeJwt('DISTRIBUTOR'))
    stubBackend(verdict({
      verdict: 'SUSPECT',
      reasons: [{ code: 'BATCH_RECALLED', impact: 'SUSPECT', message: 'This batch was recalled by the manufacturer.', eventSeq: null }],
    }))
    renderAt('/verify/TG-TEST')

    expect(await screen.findByRole('status')).toHaveTextContent('SUSPECT')
    expect(screen.getByText('This batch was recalled by the manufacturer.')).toBeInTheDocument()
  })

  it('still shows the verdict when the timeline cannot be loaded', async () => {
    tokenStore.set(fakeJwt('AUDITOR'))
    vi.stubGlobal('fetch', vi.fn(async (url: string) =>
      url.startsWith('/api/verify/') ? json(verdict({ verdict: 'AUTHENTIC' })) : json({ message: 'nope' }, 403)))
    renderAt('/verify/TG-TEST')

    expect(await screen.findByRole('status')).toHaveTextContent('AUTHENTIC')
    expect(screen.queryByText('Ledger timeline')).not.toBeInTheDocument()
    expect(screen.queryByText('nope')).not.toBeInTheDocument()
  })

  it('shows the server error when verification fails', async () => {
    tokenStore.set(fakeJwt('RETAILER'))
    stubBackend(json({ message: 'Verification service unavailable' }, 500))
    renderAt('/verify/TG-TEST')

    expect(await screen.findByText('Verification service unavailable')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('checks the tag typed into the form', async () => {
    tokenStore.set(fakeJwt('RETAILER'))
    const fetchMock = stubBackend(verdict({ tagId: 'TG-NEW' }))
    renderAt('/verify')

    await userEvent.type(screen.getByLabelText(/Tag ID/), '  TG-NEW  ')
    await userEvent.click(screen.getByRole('button', { name: 'Check' }))

    expect(await screen.findByRole('status')).toHaveTextContent('AUTHENTIC')
    expect(fetchMock).toHaveBeenCalledWith('/api/verify/TG-NEW', expect.anything())
  })
})
