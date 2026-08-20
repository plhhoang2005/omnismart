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
$env:APP_ENV = "local"
$env:FRONTEND_URL = "http://localhost:5173"
$env:SESSION_COOKIE_SECURE = "false"
$env:SESSION_COOKIE_SAME_SITE = "lax"
```

Never commit these values. In a deployed HTTPS environment, set `SESSION_COOKIE_SECURE=true` and set `FRONTEND_URL` to the exact deployed frontend origin. Prefer hosting frontend and backend on the same site. If they must be cross-site, use HTTPS and set `SESSION_COOKIE_SAME_SITE=none`.

The safe default profile is `production`. Local development must explicitly use `APP_ENV=local`. For staging, set `APP_ENV=staging`, use an HTTPS `FRONTEND_URL`, and register the staging callback URL `https://<backend-host>/login/oauth2/code/google`. Staging and production always enable the Secure session cookie and refuse to start without `FRONTEND_URL`, database credentials and an explicit `PRODUCT_MEDIA_STORAGE_ROOT`.

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
- `POST /api/v1/stores` creates a confirmed store owned by the signed-in user
- `GET /api/v1/stores` lists only the signed-in user's stores
- `GET /api/v1/stores/{storeId}` returns a store only when the user is a member
- `PATCH /api/v1/stores/{storeId}` lets an Owner confirm onboarding, rename, archive or reactivate a store
- `GET /api/v1/stores/{storeId}/members` lists members for an Owner
- `PATCH /api/v1/stores/{storeId}/members/{userId}` changes `OWNER`/`STAFF` role after store-name confirmation
- `DELETE /api/v1/stores/{storeId}/members/{userId}` revokes membership after store-name confirmation
- `POST /api/v1/stores/{storeId}/invitations` creates a time-limited invitation
- `GET /api/v1/stores/{storeId}/invitations` lists invitations for an Owner
- `DELETE /api/v1/stores/{storeId}/invitations/{id}` revokes a pending invitation after exact-email confirmation
- `GET /api/v1/invitations` lists invitations matching the signed-in user's verified email
- `POST /api/v1/invitations/{id}/accept` and `/decline` record the recipient's decision
- `POST /api/v1/stores/{storeId}/products` creates a product from human-confirmed commercial data
- `GET /api/v1/stores/{storeId}/products?page=0&size=20&search=&status=ACTIVE` searches a tenant-scoped catalog
- `GET /api/v1/stores/{storeId}/products/{productId}` returns a tenant-scoped product
- `PATCH /api/v1/stores/{storeId}/products/{productId}` updates a product using the current `version`
- `DELETE /api/v1/stores/{storeId}/products/{productId}` archives after `version` and exact-SKU confirmation
- `POST /api/v1/stores/{storeId}/products/{productId}/media` uploads validated JPEG, PNG or WebP content
- `GET /api/v1/stores/{storeId}/products/{productId}/media` lists attached images
- `PATCH /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/primary` records the user's primary-image choice
- `DELETE /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}` removes a wrong image and frees its quota slot
- `GET /api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/content` streams an authorized image

The first Google login creates one default store with `onboardingCompleted=false`. The client must ask the Owner to confirm its name through `PATCH /api/v1/stores/{storeId}` before continuing onboarding. Product mutations, media mutations and invitations are rejected with `STORE_ONBOARDING_REQUIRED` until that confirmation. Archiving requires a separate request with the exact current store name in `confirmationName`; there is intentionally no store deletion endpoint in this phase.

Invitation email delivery is deliberately deferred. The create response returns `delivery: "MANUAL"` and a one-time `invitationToken` so the team can test the workflow or share it through a controlled channel. Only the SHA-256 token hash is stored. The recipient must sign in with the exact invited email and send the token in the accept/decline request body. Invitation tokens expire after 72 hours by default (`MEMBERSHIP_INVITATION_TTL`) and are never returned by list endpoints. An Owner can revoke a mistaken pending invitation only by confirming its exact normalized email.

Role changes, Owner invitations and membership revocation require the current store name as explicit confirmation. The backend locks membership rows while changing or removing an Owner, so concurrent requests cannot leave a store without an Owner. All invitation decisions and membership changes create tenant-scoped audit records without storing raw invitation tokens.

Product SKUs are trimmed and normalized to uppercase and are unique only inside their store. Prices use exact decimal storage, and price/inventory values cannot be negative. Every product mutation is tenant-scoped and audited. `PATCH` and archive requests must send the last observed `version`; stale writes return `409` with `PRODUCT_VERSION_CONFLICT`. Archive is soft-delete only and additionally requires `confirmationSku`. The publishing module must implement `ProductPublicationGuard` before it introduces publishing jobs, so active jobs block archive with `PRODUCT_HAS_ACTIVE_PUBLISHING_JOBS`.

Product image upload is intentionally human-driven. Send multipart field `file` and optional `primary=true`; the original filename and claimed MIME type are not trusted. The server writes to temporary storage, validates image content and dimensions, generates its own object key, and only then marks media as attached. Limits default to 5 MB and 8 images per product. A bounded scheduled cleanup removes stale temporary files and stored objects that have no database record. Local filesystem storage is the current adapter; an S3-compatible adapter can replace it without changing product business logic.

The committed API contract is [docs/openapi/backend-basic.yaml](docs/openapi/backend-basic.yaml). API failures use one JSON shape containing `code`, `message`, `fieldErrors`, `traceId`, `path` and `timestamp`; the same request ID is returned in `X-Request-Id` and added to the logging context.

Start the frontend in another terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open `http://localhost:5173`.

The browser receives only the OmniSmart session cookie. Google provider tokens are discarded after authentication: they are not returned to React or stored in the application database/session. The `JSESSIONID` cookie is HttpOnly and SameSite=Lax; the separate CSRF token is intentionally readable by the frontend so it can authorize state-changing requests.

OAuth initiation and callback endpoints are rate-limited per client address and endpoint. Defaults are 10 requests per minute and can be changed with `OAUTH_RATE_LIMIT_MAX_REQUESTS` and `OAUTH_RATE_LIMIT_WINDOW`. This limiter is intentionally in-memory for the current single-backend beta topology; use a shared limiter before running multiple backend replicas. The trusted reverse proxy must replace, rather than append untrusted, forwarded client-address headers.

## Verify changes

Backend:

```powershell
Set-Location backend
.\mvnw.cmd --batch-mode verify
```

Run the database compatibility suite against a disposable PostgreSQL 17 container:

```powershell
Set-Location backend
.\mvnw.cmd --batch-mode verify -Ppostgres-it
```

The PostgreSQL profile requires Docker. It validates every Flyway migration and critical
tenant constraints on the same database family used by local and deployed environments.

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

The second migration adds explicit store lifecycle and onboarding fields. Stores are archived rather than hard-deleted during this phase.

The third migration adds membership invitation lifecycle, membership update timestamps and tenant-scoped audit logs. Database constraints restrict roles and invitation states and allow at most one pending invitation for an email in a store.

Do not edit a migration that has already been applied to a shared environment; add a new versioned migration instead.

## Documentation

- [Product and delivery plan](PLAN_PRODUCT_OMNISMART.md)
- [Local infrastructure](infra/README.md)
- [Architecture decisions](docs/adr/)
- [Google OIDC session decision](docs/adr/0002-google-oidc-session.md)
- [Tenant and store lifecycle decision](docs/adr/0003-tenant-store-lifecycle.md)
- [Membership invitation and RBAC decision](docs/adr/0004-membership-invitation-rbac.md)
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
