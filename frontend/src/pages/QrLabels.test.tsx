import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { json, renderAt, signInAs, stubFetch } from '../test/helpers'

afterEach(() => vi.unstubAllGlobals())

describe('QR labels page', () => {
  it('draws one QR code per tag in the batch', async () => {
    signInAs('MANUFACTURER')
    const fetchMock = stubFetch((url) => (url === '/api/batches/3/tags' ? json({ batchId: 3, tagIds: ['TG-A', 'TG-B'] }) : json({}, 404)))
    renderAt('/labels/3')

    expect(await screen.findByLabelText('QR label for TG-A')).toBeInTheDocument()
    expect(screen.getByLabelText('QR label for TG-B')).toBeInTheDocument()
    expect(document.querySelectorAll('svg title')).toHaveLength(2)
    expect(fetchMock).toHaveBeenCalledWith('/api/batches/3/tags', expect.anything())
  })

  it('prints the labels', async () => {
    signInAs('ADMIN')
    stubFetch(() => json({ batchId: 3, tagIds: ['TG-A'] }))
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    renderAt('/labels/3')

    await userEvent.click(await screen.findByRole('button', { name: 'Print labels' }))
    expect(print).toHaveBeenCalled()
  })

  it('says so when the batch has no tags yet', async () => {
    signInAs('MANUFACTURER')
    stubFetch(() => json({ batchId: 3, tagIds: [] }))
    renderAt('/labels/3')
    expect(await screen.findByText(/no tags yet/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Print labels' })).toBeDisabled()
  })

  it('is linked from each batch in the Product Registry', async () => {
    signInAs('MANUFACTURER')
    stubFetch((url) => {
      if (url === '/api/products') return json([{ id: 1, organizationId: 1, name: 'Aspirin', productType: 'pharma' }])
      if (url === '/api/products/1/batches') return json([{ id: 3, productId: 1, label: 'B-1', recalled: false }])
      return json({}, 404)
    })
    renderAt('/products')

    await userEvent.click(await screen.findByRole('button', { name: 'Batches' }))
    expect(await screen.findByRole('link', { name: 'QR labels' })).toHaveAttribute('href', '/labels/3')
  })
})
