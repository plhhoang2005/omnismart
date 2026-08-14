import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('OmniSmart authentication experience', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/')
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('offers backend-managed Google login to a guest', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
    render(<App />)

    expect(await screen.findByRole('link', { name: 'Đăng nhập bằng Google' }))
      .toHaveAttribute('href', 'http://localhost:8080/oauth2/authorization/google')
    expect(screen.getByText('Human approval by default')).toBeInTheDocument()
  })

  it('shows the authenticated user and store role returned by the backend', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 'user-1',
        email: 'owner@example.com',
        displayName: 'Store Owner',
        memberships: [{
          storeId: 'store-1',
          storeName: 'Owned Store',
          storeSlug: 'owned-store',
          role: 'OWNER',
        }],
      }),
    }))
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Xin chào, Store Owner' })).toBeInTheDocument()
    expect(screen.getByText('Owned Store')).toBeInTheDocument()
    expect(screen.getByText('OWNER')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument()
  })

  it('shows a safe message when Google rejects the callback', async () => {
    window.history.replaceState({}, '', '/login?error=oauth')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Google chưa thể xác thực')
  })

  it('gets a CSRF token before logging out', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: 'user-1',
          email: 'owner@example.com',
          displayName: 'Store Owner',
          memberships: [],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
      })
      .mockResolvedValueOnce({ ok: true, status: 204 })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.click(await screen.findByRole('button', { name: 'Đăng xuất' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3))
    expect(fetchMock).toHaveBeenNthCalledWith(3, 'http://localhost:8080/api/v1/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
    })
    expect(await screen.findByRole('link', { name: 'Đăng nhập bằng Google' })).toBeInTheDocument()
  })
})
