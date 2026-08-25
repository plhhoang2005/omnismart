import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DevLoginForm from './DevLoginForm'

describe('DevLoginForm', () => {
  afterEach(cleanup)

  it('submits a separate local test identity', async () => {
    const onLogin = vi.fn().mockResolvedValue(undefined)
    render(<DevLoginForm onLogin={onLogin} />)

    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Team Tester' } })
    fireEvent.change(screen.getByLabelText('Test email'), { target: { value: 'tester@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in for testing' }))

    await waitFor(() => expect(onLogin).toHaveBeenCalledWith('tester@example.com', 'Team Tester'))
  })

  it('shows a safe error when local login is disabled', async () => {
    const onLogin = vi.fn().mockRejectedValue(new Error('disabled'))
    render(<DevLoginForm onLogin={onLogin} />)

    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Team Tester' } })
    fireEvent.change(screen.getByLabelText('Test email'), { target: { value: 'tester@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in for testing' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Local login failed')
  })
})
