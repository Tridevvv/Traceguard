import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Checkpoint, RoutePolicy } from '../types'
import { RoutePolicyEditor } from './RoutePolicyEditor'

const CHECKPOINTS: Checkpoint[] = [
  { id: 1, organizationId: 1, name: 'Factory', locationName: 'Boston', latitude: 0, longitude: 0 },
  { id: 2, organizationId: 1, name: 'Warehouse', locationName: 'Albany', latitude: 0, longitude: 0 },
  { id: 3, organizationId: 1, name: 'Pharmacy', locationName: 'Chicago', latitude: 0, longitude: 0 },
]

const PHARMA: RoutePolicy = {
  id: 9,
  productType: 'pharma',
  maxTotalHours: 168,
  steps: [{ checkpointId: 1, maxHoursFromPrevious: null }, { checkpointId: 2, maxHoursFromPrevious: 48 }],
}

/** Fake backend: GET returns the given policies, POST and PUT echo the body back with an id. */
function stubBackend(existing: RoutePolicy[]) {
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (init?.method === 'POST' || init?.method === 'PUT') {
      return new Response(JSON.stringify({ id: 42, ...JSON.parse(String(init.body)) }), { status: 200 })
    }
    return new Response(JSON.stringify(url === '/api/route-policies' ? existing : []), { status: 200 })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** The parsed JSON body of the first call made with the given method. */
function sentBody(fetchMock: ReturnType<typeof stubBackend>, method: string) {
  const call = fetchMock.mock.calls.find(([, init]) => init?.method === method)
  return call ? { url: call[0], body: JSON.parse(String(call[1]?.body)) } : null
}

async function pickCheckpoint(stop: number, name: string) {
  const row = screen.getByText(`Stop ${stop}`).parentElement as HTMLElement
  await userEvent.click(within(row).getByRole('combobox'))
  await userEvent.click(await screen.findByRole('option', { name: new RegExp(name) }))
}

afterEach(() => vi.unstubAllGlobals())

describe('RoutePolicyEditor', () => {
  it('lists existing routes with their checkpoint names in order', async () => {
    stubBackend([PHARMA])
    render(<RoutePolicyEditor checkpoints={CHECKPOINTS} />)

    expect(await screen.findByText('pharma (max 168 h)')).toBeInTheDocument()
    expect(screen.getByText('Factory → Warehouse')).toBeInTheDocument()
  })

  it('creates a route in the chosen order, with no leg limit on the first stop', async () => {
    const fetchMock = stubBackend([])
    render(<RoutePolicyEditor checkpoints={CHECKPOINTS} />)

    await userEvent.type(screen.getByLabelText(/Product type/), ' electronics ')
    await pickCheckpoint(1, 'Warehouse')
    await pickCheckpoint(2, 'Factory')
    await userEvent.type(screen.getByLabelText(/Max hours from previous/), '24')
    // Swap the two stops: Factory first, then Warehouse.
    await userEvent.click(screen.getByLabelText('Move stop 2 up'))
    await userEvent.click(screen.getByRole('button', { name: 'Create route' }))

    expect(await screen.findByText(/Route for "electronics" saved with 2 stop/)).toBeInTheDocument()
    expect(sentBody(fetchMock, 'POST')).toEqual({
      url: '/api/route-policies',
      body: {
        productType: 'electronics',
        maxTotalHours: 168,
        steps: [
          { checkpointId: 1, maxHoursFromPrevious: null },
          { checkpointId: 2, maxHoursFromPrevious: null },
        ],
      },
    })
  })

  it('edits an existing route with PUT and keeps its product type', async () => {
    const fetchMock = stubBackend([PHARMA])
    render(<RoutePolicyEditor checkpoints={CHECKPOINTS} />)

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    expect(screen.getByLabelText(/Product type/)).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Add stop' }))
    await pickCheckpoint(3, 'Pharmacy')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText(/Route for "pharma" saved with 3 stop/)).toBeInTheDocument()
    const sent = sentBody(fetchMock, 'PUT')
    expect(sent?.url).toBe('/api/route-policies/9')
    expect(sent?.body.steps).toEqual([
      { checkpointId: 1, maxHoursFromPrevious: null },
      { checkpointId: 2, maxHoursFromPrevious: 48 },
      { checkpointId: 3, maxHoursFromPrevious: null },
    ])
  })

  it('keeps the save button disabled until every stop has a checkpoint', async () => {
    stubBackend([])
    render(<RoutePolicyEditor checkpoints={CHECKPOINTS} />)

    await userEvent.type(screen.getByLabelText(/Product type/), 'pharma')
    await pickCheckpoint(1, 'Factory')
    expect(screen.getByRole('button', { name: 'Create route' })).toBeDisabled()
    await pickCheckpoint(2, 'Warehouse')
    expect(screen.getByRole('button', { name: 'Create route' })).toBeEnabled()
  })
})
