import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, renderAt, signInAs, stubFetch } from '../test/helpers'
import type { AlertItem } from '../types'
import { sortAlerts } from './alertOrder'

function alertItem(overrides: Partial<AlertItem>): AlertItem {
  return {
    id: 1,
    tagId: 'TG-DEMO-CLONED',
    code: 'DUPLICATE_PRESENCE',
    severity: 'HIGH',
    status: 'OPEN',
    message: 'Seen in Boston and Chicago 40 s apart',
    createdAt: '2026-11-18T10:00:00Z',
    updatedAt: '2026-11-18T10:00:00Z',
    ...overrides,
  }
}

afterEach(() => vi.unstubAllGlobals())

describe('sortAlerts', () => {
  it('puts the highest severity first, then the newest', () => {
    const sorted = sortAlerts([
      alertItem({ id: 1, severity: 'LOW', createdAt: '2026-11-18T12:00:00Z' }),
      alertItem({ id: 2, severity: 'HIGH', createdAt: '2026-11-17T10:00:00Z' }),
      alertItem({ id: 3, severity: 'HIGH', createdAt: '2026-11-18T09:00:00Z' }),
      alertItem({ id: 4, severity: 'MEDIUM' }),
    ])
    expect(sorted.map((a) => a.id)).toEqual([3, 2, 4, 1])
  })
})

describe('Alerts page', () => {
  it('lists open alerts by default with a link to verify the tag', async () => {
    signInAs('ADMIN')
    const fetchMock = stubFetch((url) => (url.startsWith('/api/alerts') ? json([alertItem({})]) : json({}, 404)))
    renderAt('/alerts')

    expect(await screen.findByText('DUPLICATE_PRESENCE')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'TG-DEMO-CLONED' })).toHaveAttribute('href', '/verify/TG-DEMO-CLONED')
    expect(fetchMock).toHaveBeenCalledWith('/api/alerts?status=OPEN', expect.anything())
  })

  it('loads another status when a filter tab is chosen', async () => {
    signInAs('ADMIN')
    const fetchMock = stubFetch((url) => (url.startsWith('/api/alerts') ? json([]) : json({}, 404)))
    renderAt('/alerts')
    expect(await screen.findByText('No open alerts.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'All' }))
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/alerts', expect.anything()))
    expect(await screen.findByText('No alerts.')).toBeInTheDocument()
  })

  it('resolves an alert with a PATCH and reloads the list', async () => {
    signInAs('MANUFACTURER')
    let resolved = false
    const fetchMock = stubFetch((url, init) => {
      if (url === '/api/alerts/1' && init?.method === 'PATCH') {
        resolved = true
        return json(alertItem({ status: 'RESOLVED' }))
      }
      if (url.startsWith('/api/alerts')) return json(resolved ? [] : [alertItem({})])
      return json({}, 404)
    })
    renderAt('/alerts')

    const row = (await screen.findByText('DUPLICATE_PRESENCE')).closest('tr')!
    await userEvent.click(within(row).getByRole('button', { name: 'Resolve' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/alerts/1', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ status: 'RESOLVED' }),
    })))
    expect(await screen.findByText('No open alerts.')).toBeInTheDocument()
  })

  it('gives auditors a read-only view with no action buttons', async () => {
    signInAs('AUDITOR')
    stubFetch((url) => (url.startsWith('/api/alerts') ? json([alertItem({})]) : json({}, 404)))
    renderAt('/alerts')

    expect(await screen.findByText('DUPLICATE_PRESENCE')).toBeInTheDocument()
    expect(screen.getByText(/read-only access/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument()
  })

  it('is not available to retailers', async () => {
    signInAs('RETAILER')
    stubFetch(() => json({}, 404))
    renderAt('/alerts')
    expect(await screen.findByText(/cannot open this page/)).toBeInTheDocument()
  })
})
