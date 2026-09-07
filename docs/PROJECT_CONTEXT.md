# OmniSmart Project Context

Last verified against the repository on 2026-09-07.

## Product goal and current scope

OmniSmart is an open-source product for helping small stores turn structured product data into reviewed, channel-ready marketing content through a traceable workflow. AI output must be reviewed by a human and must not invent source-of-truth commercial facts such as price, inventory, SKU, or promotions.

The implemented foundation currently includes:

- Google OpenID Connect login with a server-managed session;
- application users, stores, tenant membership, Owner/Staff roles, and invitations;
- tenant-scoped product catalog and validated product-image storage;
- tenant-scoped manual content drafts, immutable versions, and Owner approval/rejection;
- audit records, consistent API errors, request correlation, and health endpoints;
- a basic React authentication and product-workflow landing experience;
- PostgreSQL migrations, automated tests, Docker images, and local PostgreSQL Compose configuration.

AI drafting, Google Sheets/CSV import, invitation email delivery, publishing connectors, analytics, approved-content revisions, and a complete application UI are planned but not implemented in the current source tree.

## Technology

| Area | Current technology |
|---|---|
| Backend | Java 21 target, Spring Boot 4.1.0, Maven Wrapper |
| API | Spring MVC REST API, OpenAPI 3.1 |
| Security | Spring Security, Google OAuth2/OIDC, server session, CSRF |
| Persistence | Spring Data JPA/Hibernate, Flyway |
| Database | PostgreSQL 17; H2 for fast automated tests |
| Integration tests | JUnit, MockMvc, AssertJ, Spring Security Test, Testcontainers |
| Frontend | React 19.2, TypeScript 6.0, Vite 8.2, Vitest 4.1, oxlint |
| Local infrastructure | Docker Compose |
| Containers | Eclipse Temurin 21, Node 24, Nginx 1.29 |
| Automation | GitHub Actions and Dependabot |

Dependency declarations in `backend/pom.xml`, `frontend/package.json`, and their lock/wrapper files are the source of truth for exact versions.

## Architecture and modules

The repository is a monorepo:

```text
backend/   Spring Boot modular monolith, tests, and Flyway migrations
frontend/  React single-page application
infra/     Local PostgreSQL Docker Compose configuration
docs/      Product, API, architecture decision, and workflow documents
.github/   CI, security, release, issue, and pull-request configuration
tasks/     Lightweight backlog and current-task records
```

The backend is a single deployable application organized by business domain:

| Package | Responsibility |
|---|---|
| `auth` | OIDC login, provider-token disposal, OAuth rate limiting |
| `identity` | Application user provisioning and current-user context |
| `store` | Store lifecycle, tenant membership checks, operation guards |
| `membership` | Members, roles, invitations, and owner safeguards |
| `catalog` | Products, media, validation, cleanup, and storage adapter |
| `content` | Manual drafts, immutable content versions, approval state machine |
| `audit` | Tenant-scoped audit records |
| `common.api` | Stable API errors and request correlation |
| `system` | Public system status |

Controllers define the HTTP boundary, services hold transactional business behavior, repositories own persistence queries, and request DTOs use Bean Validation. Store-owned resource lookups must include both resource and store identifiers.

The frontend is currently a small SPA that calls the backend with session credentials. It handles guest, authenticated, OAuth-error, CSRF logout, and transient network states. It is not yet a full catalog or content-management UI.

## Data and storage

- Flyway owns schema evolution. Migrations `V1` through `V7` are present.
- Never edit a migration already used by a shared environment; add a new version after approval.
- PostgreSQL is the runtime database. H2 is used for fast tests, while the `postgres-it` profile validates PostgreSQL-specific migrations and tenant constraints with a disposable PostgreSQL 17 Testcontainer.
- Store lifecycle and product removal use recoverable archive behavior rather than hard deletion.
- Product updates use optimistic locking.
- Content edits and approval transitions use optimistic locking; submitted versions and approval history are retained.
- Product media is currently stored on the local filesystem behind `MediaStorage`. Multi-instance environments require a shared implementation before rollout.

## External services

- Google OIDC is the only implemented runtime external integration.
- Provider access and refresh tokens are deliberately not retained because the application does not currently call Google APIs for the user.
- Gemini, Google Sheets, email delivery, channel publishing, and S3-compatible storage are planned or configuration placeholders, not implemented integrations.

## Local configuration and secrets

Copy `.env.example` to an ignored `.env` for local development. Real credentials belong in a local secret store, IDE run configuration, shell environment, or approved deployment secret store. Never commit or display `.env`, OAuth credentials, API keys, database dumps, connector tokens, or personal data.

The default application profile is production-safe. Local development must explicitly set `APP_ENV=local`. Staging and production require explicit database, frontend-origin, cookie, and media-storage configuration.

## Build, run, and test

Start PostgreSQL from the repository root:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d
docker compose --env-file .env -f infra/compose.yaml ps
```

Run and verify the backend:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
.\mvnw.cmd --batch-mode verify
.\mvnw.cmd --batch-mode verify -Ppostgres-it
```

Run and verify the frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
npm run lint
npm test -- --run
npm run build
```

Validate Compose without using production data:

```powershell
docker compose --env-file .env.example -f infra/compose.yaml config --quiet
```

The PostgreSQL integration profile requires a working Docker engine and may download the PostgreSQL image on first use.

## Code and Git conventions

- `.editorconfig` requires UTF-8, LF, final newlines, spaces, two-space default indentation, and four-space Java indentation.
- Java follows package-by-domain organization, constructor injection, explicit transactions, Bean Validation, and tenant-scoped repository methods.
- Frontend code uses TypeScript functional components and project lint/type/build scripts.
- Tests describe behavior and cover meaningful success, authorization, validation, conflict, and tenant-isolation paths.
- Use short-lived branches and Conventional Commits. All changes go through a pull request and review before merging to `main`.

## Important technical decisions

- The backend remains a modular monolith for the MVP; RabbitMQ, Redis, microservices, and Kubernetes are intentionally deferred.
- Authentication uses Google OIDC with server-managed sessions and CSRF protection.
- Authorization is derived on the backend from store membership; frontend roles are never trusted.
- Missing and unauthorized cross-tenant resources both return `404` to avoid resource disclosure.
- Invitation tokens are time limited, single use, stored only as hashes, and omitted from list responses and audit data.
- Product media is validated from content rather than trusting file names or claimed MIME types.
- Content and approval reads/writes are tenant-scoped; Owner-only review actions return `404` to Staff and outsiders.
- Human confirmation is required for sensitive lifecycle, role, archive, and publishing decisions.

## Current limitations and unknowns

- Real Google login requires team-owned OAuth credentials and registered redirect URIs.
- OAuth rate limiting and HTTP sessions are in-memory/single-instance mechanisms; horizontal scaling needs shared equivalents or an approved topology.
- Local media is not suitable for multiple backend replicas.
- Production hosting, secret manager, backup/restore implementation, monitoring provider, and connector credentials are not established by the repository.
- Current build and test health must be verified for each task; the existence of generated artifacts is not evidence of a passing build.
- `docs/BAO_CAO_BACKEND_CO_BAN.md` contains known stale statements about the Gemini placeholder and media adapter name. Reconciliation is tracked in `tasks/BACKLOG.md` and is outside the workflow-setup scope.
