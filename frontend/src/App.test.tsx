import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider } from './auth/AuthProvider'
import { tokenStore } from './api/client'

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

afterEach(() => vi.unstubAllGlobals())

describe('routing and access', () => {
  it('sends a signed-out visitor from a protected page to the login form', () => {
    renderAt('/products')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('tells a role without access that it cannot open the page', () => {
    tokenStore.set(fakeJwt('RETAILER'))
    renderAt('/admin')
    expect(screen.getByText(/Your role \(RETAILER\) cannot open this page/)).toBeInTheDocument()
  })

  it('shows only the menu items a role may use', () => {
    tokenStore.set(fakeJwt('AUDITOR'))
    renderAt('/dashboard')
    expect(screen.getByRole('link', { name: 'Verify a Tag' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Admin Panel' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Scan Console' })).not.toBeInTheDocument()
  })
})

describe('login', () => {
  it('signs in and lands on the dashboard', async () => {
    const token = fakeJwt('MANUFACTURER')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ token, role: 'MANUFACTURER' }), { status: 200 })))
    renderAt('/login')

    await userEvent.type(screen.getByLabelText(/Email/), 'user@test.local')
    await userEvent.type(screen.getByLabelText(/Password/), 'Passw0rd!x')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Welcome' })).toBeInTheDocument()
    expect(tokenStore.get()).toBe(token)
  })

  it('shows an error for wrong credentials', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))
    renderAt('/login')

    await userEvent.type(screen.getByLabelText(/Email/), 'user@test.local')
    await userEvent.type(screen.getByLabelText(/Password/), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(screen.getByText('Invalid email or password.')).toBeInTheDocument())
    expect(tokenStore.get()).toBeNull()
  })
})
