import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, renderAt, stubFetch } from '../test/helpers'
import type { Verdict } from '../types'

const counterfeit: Verdict = {
  tagId: 'TG-DEMO-CLONED',
  verdict: 'COUNTERFEIT',
  reasons: [{ code: 'DUPLICATE_PRESENCE', impact: 'COUNTERFEIT', message: 'Seen in two places at once', eventSeq: 1 }],
  timeline: [
    { seq: 0, eventType: 'REGISTERED', checkpointName: 'Factory', locationName: 'Boston, MA', occurredAt: '2026-11-18T10:00:00Z' },
    { seq: 1, eventType: 'RECEIVED', checkpointName: 'Pharmacy', locationName: 'Chicago, IL', occurredAt: '2026-11-18T10:00:40Z' },
  ],
  verifiedAt: '2026-11-18T12:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

describe('Public consumer check', () => {
  it('shows the verdict and journey without signing in', async () => {
    const fetchMock = stubFetch((url) => (url === '/api/public/verify/TG-DEMO-CLONED' ? json(counterfeit) : json({}, 404)))
    renderAt('/check/TG-DEMO-CLONED')

    expect(await screen.findByRole('status')).toHaveTextContent('COUNTERFEIT')
    expect(screen.getByText('DUPLICATE_PRESENCE · event #1')).toBeInTheDocument()
    expect(screen.getByText('RECEIVED at Pharmacy, Chicago, IL')).toBeInTheDocument()
    // No token is sent: the public endpoint is used as a consumer would.
    const [, init] = fetchMock.mock.calls[0]
    expect(JSON.stringify(init)).not.toContain('Authorization')
  })

  it('is reached from the landing page verify box', async () => {
    const fetchMock = stubFetch((url) => (url.startsWith('/api/public/verify/') ? json({ ...counterfeit, tagId: 'TG-1' }) : json({}, 404)))
    renderAt('/')

    await userEvent.type(screen.getByLabelText('Tag ID'), 'TG-1')
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/public/verify/TG-1', expect.anything()))
    expect(await screen.findByRole('heading', { name: 'Check a product' })).toBeInTheDocument()
  })

  it('accepts a pasted check link from a QR label', async () => {
    const fetchMock = stubFetch((url) => (url.startsWith('/api/public/verify/') ? json({ ...counterfeit, tagId: 'TG-7' }) : json({}, 404)))
    renderAt('/check')

    await userEvent.type(screen.getByLabelText('Tag ID'), 'https://traceguard.example/check/TG-7')
    await userEvent.click(screen.getByRole('button', { name: 'Check' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/public/verify/TG-7', expect.anything()))
  })

  it('explains the rate limit in plain words', async () => {
    stubFetch(() => json({ message: 'Too Many Requests' }, 429))
    renderAt('/check/TG-1')
    expect(await screen.findByText(/Too many checks from this network/)).toBeInTheDocument()
  })
})
