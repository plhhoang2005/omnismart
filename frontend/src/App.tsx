import { useEffect, useState } from 'react'
import './App.css'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

type Membership = {
  storeId: string
  storeName: string
  storeSlug: string
  role: 'OWNER' | 'STAFF'
}

type CurrentUser = {
  id: string
  email: string
  displayName: string
  memberships: Membership[]
}

type AuthState =
  | { status: 'loading' }
  | { status: 'guest' }
  | { status: 'authenticated'; user: CurrentUser }

const workflow = [
  {
    step: '01',
    title: 'Nhập dữ liệu',
    description: 'Đồng bộ sản phẩm từ Google Sheets, CSV hoặc biểu mẫu.',
  },
  {
    step: '02',
    title: 'AI tạo bản nháp',
    description: 'Tạo nội dung phù hợp từng kênh mà không bịa giá hay tồn kho.',
  },
  {
    step: '03',
    title: 'Con người duyệt',
    description: 'Staff chỉnh sửa và Owner phê duyệt trước khi xuất bản.',
  },
  {
    step: '04',
    title: 'Đăng và đo lường',
    description: 'Lên lịch, theo dõi trạng thái và ghi nhận thời gian tiết kiệm.',
  },
]

function App() {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' })
  const oauthError = new URLSearchParams(window.location.search).get('error') === 'oauth'

  useEffect(() => {
    void loadCurrentUser()
  }, [])

  async function loadCurrentUser() {
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/me`, {
        credentials: 'include',
      })
      if (!response.ok) {
        setAuth({ status: 'guest' })
        return
      }
      setAuth({ status: 'authenticated', user: await response.json() as CurrentUser })
    } catch {
      setAuth({ status: 'guest' })
    }
  }

  async function logout() {
    try {
      const csrfResponse = await fetch(`${apiBaseUrl}/api/v1/auth/csrf`, {
        credentials: 'include',
      })
      if (!csrfResponse.ok) {
        return
      }
      const csrf = await csrfResponse.json() as { headerName: string; token: string }
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/logout`, {
        method: 'POST',
        credentials: 'include',
        headers: { [csrf.headerName]: csrf.token },
      })
      if (response.ok) {
        setAuth({ status: 'guest' })
      }
    } catch {
      // Keep the authenticated UI so the user can retry a transient network failure.
    }
  }

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="#top" aria-label="OmniSmart - trang chủ">
          <span className="brand-mark" aria-hidden="true">O</span>
          <span>OmniSmart</span>
        </a>
        <AuthAction auth={auth} onLogout={logout} />
      </header>

      {oauthError && (
        <div className="auth-alert" role="alert">
          Google chưa thể xác thực tài khoản. Vui lòng thử lại hoặc liên hệ quản trị viên.
        </div>
      )}

      <section className="hero" id="top">
        <div className="eyebrow">AI-assisted content operations</div>
        <h1>Một quy trình rõ ràng từ sản phẩm đến nội dung đã duyệt.</h1>
        <p className="hero-copy">
          OmniSmart giúp cửa hàng nhỏ tạo, kiểm duyệt và theo dõi nội dung bán hàng đa kênh,
          trong khi con người vẫn giữ quyền quyết định cuối cùng.
        </p>
        <div className="hero-actions">
          {auth.status === 'authenticated' ? (
            <a className="button button-primary" href="#account">Mở không gian làm việc</a>
          ) : (
            <a className="button button-primary" href={`${apiBaseUrl}/oauth2/authorization/google`}>
              Đăng nhập bằng Google
            </a>
          )}
          <a className="button button-secondary" href="#workflow">Xem quy trình</a>
        </div>
      </section>

      {auth.status === 'authenticated' && <AccountPanel user={auth.user} />}

      <section className="workflow-section" id="workflow" aria-labelledby="workflow-title">
        <div className="section-heading">
          <div>
            <span className="section-kicker">Luồng MVP</span>
            <h2 id="workflow-title">Bốn bước, một trạng thái xuyên suốt</h2>
          </div>
          <p>Phiên bản nền móng đã sẵn sàng để phát triển từng lát cắt end-to-end.</p>
        </div>

        <div className="workflow-grid">
          {workflow.map((item) => (
            <article className="workflow-card" key={item.step}>
              <span className="step">{item.step}</span>
              <h3>{item.title}</h3>
              <p>{item.description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="principle" aria-label="Nguyên tắc sản phẩm">
        <span className="principle-icon" aria-hidden="true">✓</span>
        <div>
          <strong>Human approval by default</strong>
          <p>AI chỉ tạo gợi ý. Nội dung phải được duyệt trước khi đưa tới khách hàng.</p>
        </div>
      </section>

      <footer>
        <span>Open source · Apache-2.0</span>
        <a href="https://github.com/plhhoang2005/omnismart">GitHub repository</a>
      </footer>
    </main>
  )
}

function AuthAction({ auth, onLogout }: { auth: AuthState; onLogout: () => Promise<void> }) {
  if (auth.status === 'loading') {
    return <span className="release-badge">Đang kiểm tra phiên…</span>
  }
  if (auth.status === 'guest') {
    return <a className="header-login" href={`${apiBaseUrl}/oauth2/authorization/google`}>Đăng nhập</a>
  }
  return (
    <div className="user-action">
      <span>{auth.user.displayName}</span>
      <button type="button" onClick={() => void onLogout()}>Đăng xuất</button>
    </div>
  )
}

function AccountPanel({ user }: { user: CurrentUser }) {
  return (
    <section className="account-panel" id="account" aria-labelledby="account-title">
      <div>
        <span className="section-kicker">Phiên Google đã xác thực</span>
        <h2 id="account-title">Xin chào, {user.displayName}</h2>
        <p>{user.email}</p>
      </div>
      <div className="membership-list">
        {user.memberships.map((membership) => (
          <article key={membership.storeId}>
            <span>{membership.role}</span>
            <strong>{membership.storeName}</strong>
            <small>/{membership.storeSlug}</small>
          </article>
        ))}
      </div>
    </section>
  )
}

export default App
