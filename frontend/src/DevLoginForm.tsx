import { useState } from 'react'
import type { FormEvent } from 'react'

type Props = {
  onLogin: (email: string, displayName: string) => Promise<void>
}

function DevLoginForm({ onLogin }: Props) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(false)
    try {
      await onLogin(email, displayName)
    } catch {
      setError(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="dev-login" onSubmit={(event) => void submit(event)}>
      <div>
        <strong>Team test login</strong>
        <small>Local development only - Google is not contacted.</small>
      </div>
      <label>
        Display name
        <input
          required
          maxLength={200}
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
      </label>
      <label>
        Test email
        <input
          required
          type="email"
          maxLength={320}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
      </label>
      <button className="button button-secondary" type="submit" disabled={submitting}>
        {submitting ? 'Signing in...' : 'Sign in for testing'}
      </button>
      {error && <span className="dev-login-error" role="alert">Local login failed. Check both DEV_LOGIN flags.</span>}
    </form>
  )
}

export default DevLoginForm
