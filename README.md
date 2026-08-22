# OmniSmart

OmniSmart is an open-source product for helping small stores turn structured product data into reviewed, channel-ready marketing content through one traceable workflow.

> Status: authenticated application foundation. The repository includes Google OIDC login, store membership authorization, a buildable Spring Boot backend, React frontend, PostgreSQL local infrastructure and automated quality gates.

## Product goal

```text
Google Sheets / CSV / form
→ normalized product data
→ AI-assisted content draft
→ human review and approval
→ scheduled publishing or safe export
→ status and outcome dashboard
```

AI output is always reviewed by a human before publication. OmniSmart must never invent structured facts such as price, inventory or promotions.

## Technology foundation

| Area | Technology |
|---|---|
| Backend | Java 21 target, Spring Boot 4.1, Maven Wrapper |
| Frontend | React 19, TypeScript, Vite, Vitest |
| Database | PostgreSQL 17, Flyway migrations |
| Local infrastructure | Docker Compose |
| Automation | GitHub Actions and Dependabot |

The backend targets Java 21 and can be built by the JDK 26 currently used by the project environment.

## Repository layout

```text
backend/   Spring Boot REST API and Flyway migrations
frontend/  React and TypeScript single-page application
infra/     Local PostgreSQL infrastructure
docs/      Architecture decisions and product documentation
.github/   CI, security, release and contribution governance
```

## Prerequisites

- Java 21-26
- Node.js 24+ and npm
- Docker Desktop with Docker Compose
- A Google Cloud OAuth 2.0 Web client for real sign-in

Maven does not need to be installed globally; use the committed Maven Wrapper.

## Run locally

Create local configuration:

```powershell
Copy-Item .env.example .env
```

Create a Web application OAuth client in Google Cloud and register this exact local redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

Keep the real values only in your local secret store, IDE run configuration, or shell environment. For a PowerShell development session:

```powershell
$env:GOOGLE_CLIENT_ID = "your-local-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-local-client-secret"
$env:FRONTEND_URL = "http://localhost:5173"
$env:SESSION_COOKIE_SECURE = "false"
$env:SESSION_COOKIE_SAME_SITE = "lax"
```

Never commit these values. In a deployed HTTPS environment, set `SESSION_COOKIE_SECURE=true` and set `FRONTEND_URL` to the exact deployed frontend origin. Prefer hosting frontend and backend on the same site. If they must be cross-site, use HTTPS and set `SESSION_COOKIE_SAME_SITE=none`.

Start PostgreSQL:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

Start the backend in one terminal:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

The backend is available at `http://localhost:8080`:

- `GET /actuator/health`
- `GET /api/v1/system/status`
- `GET /oauth2/authorization/google` starts Google login
- `GET /api/v1/me` returns the signed-in user and store memberships
- `GET /api/v1/auth/csrf` returns the CSRF token used by the logout request
- `POST /api/v1/auth/logout` invalidates the session

Start the frontend in another terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open `http://localhost:5173`.

The browser receives only the OmniSmart session cookie. Google provider tokens are discarded after authentication: they are not returned to React or stored in the application database/session. The `JSESSIONID` cookie is HttpOnly and SameSite=Lax; the separate CSRF token is intentionally readable by the frontend so it can authorize state-changing requests.

## Verify changes

Backend:

```powershell
Set-Location backend
.\mvnw.cmd --batch-mode verify
```

Frontend:

```powershell
Set-Location frontend
npm run lint
npm test -- --run
npm run build
```

Validate Compose configuration:

```powershell
docker compose --env-file .env.example -f infra/compose.yaml config --quiet
```

## Database baseline

Flyway owns database schema changes. The first migration creates:

- `app_user`
- `store`
- `store_member`

Do not edit a migration that has already been applied to a shared environment; add a new versioned migration instead.

## Documentation

- [Product and delivery plan](PLAN_PRODUCT_OMNISMART.md)
- [Local infrastructure](infra/README.md)
- [Architecture decisions](docs/adr/)
- [Google OIDC session decision](docs/adr/0002-google-oidc-session.md)
- [Contribution guide](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

## Development workflow

1. Start from an issue with acceptance criteria.
2. Create a short-lived branch such as `feat/5-google-sso`.
3. Use Conventional Commits.
4. Open a pull request and complete the checklist.
5. Merge only after review and required checks pass.

## Security

Do not commit credentials, `.env` files, OAuth secrets, database dumps or personal data. Report security concerns according to [SECURITY.md](SECURITY.md).

## License

Copyright 2026 Pham Le Huy Hoang and OmniSmart contributors.

Licensed under the [Apache License 2.0](LICENSE).
