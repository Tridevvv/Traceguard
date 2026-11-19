import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ScanConsole } from './ScanConsole'

/** Fake backend: one checkpoint, and the simulated scan is echoed back as event #1. */
function stubBackend() {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/checkpoints') {
      return new Response(JSON.stringify([{ id: 7, organizationId: 1, name: 'Factory', locationName: 'Boston', latitude: 0, longitude: 0 }]))
    }
    if (url === '/api/scans/simulate') {
      const body = JSON.parse(String(init?.body))
      return new Response(JSON.stringify({ id: 1, ...body, occurredAt: body.timestamp ?? '2026-11-19T10:00:00Z', seq: 1, prevHash: '0', hash: 'a' }))
    }
    return new Response(JSON.stringify([]))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function scanBody(fetchMock: ReturnType<typeof stubBackend>) {
  const call = fetchMock.mock.calls.find(([url]) => url === '/api/scans/simulate')
  return JSON.parse(String(call?.[1]?.body))
}

async function fillAndScan(time?: string) {
  await userEvent.type(screen.getByLabelText(/Tag ID/), 'TG-1')
  await userEvent.click(screen.getByRole('combobox', { name: /Checkpoint/ }))
  await userEvent.click(await screen.findByRole('option', { name: /Factory/ }))
  if (time) await userEvent.type(screen.getByLabelText(/Scan time/), time)
  await userEvent.click(screen.getByRole('button', { name: 'Scan' }))
}

afterEach(() => vi.unstubAllGlobals())

describe('ScanConsole', () => {
  it('sends no timestamp when the scan time is left empty', async () => {
    const fetchMock = stubBackend()
    render(<ScanConsole />)

    await fillAndScan()

    expect(await screen.findByText('Scan recorded as event #1.')).toBeInTheDocument()
    expect(scanBody(fetchMock)).toEqual({ tagId: 'TG-1', checkpointId: 7, eventType: 'RECEIVED' })
  })

  it('sends the chosen scan time as an ISO timestamp', async () => {
    const fetchMock = stubBackend()
    render(<ScanConsole />)

    await fillAndScan('2026-11-01T09:30')

    expect(await screen.findByText('Scan recorded as event #1.')).toBeInTheDocument()
    expect(scanBody(fetchMock).timestamp).toBe(new Date('2026-11-01T09:30').toISOString())
  })
})
