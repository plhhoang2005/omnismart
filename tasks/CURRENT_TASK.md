# Current Task

Keep facts, assumptions, decisions, and results distinct. Reset this file only after the completed task has been handed off and the next task is selected.

## Identity

- **Task ID:** `VIBE-1`
- **Task name:** `Backend Content & Approval Core`
- **Status:** `DONE`
- **Branch:** `feat/content-approval-core`

## Goal and context

- **Goal:** A store member can create and version a manual product content draft, submit it for review, and an Owner can approve or reject the exact submitted version.
- **Context:** The authenticated catalog foundation, tenant membership, RBAC, audit log, Flyway, optimistic locking, OpenAPI, and PostgreSQL Testcontainers suite already exist. Content and approval are not implemented yet.

## Requirements

- Verify the backend foundation and PostgreSQL integration suite before implementation.
- Add tenant-scoped content items, immutable content versions, and approval attempts.
- Enforce Owner/Staff permissions, workflow transitions, optimistic locking, and audit logging.
- Add a new Flyway migration, REST API, OpenAPI contract, and automated tests.

## Scope

### In scope

- Backend-only manual content workflow linked to an active Product.
- States `DRAFT`, `IN_REVIEW`, `APPROVED`, and `REJECTED`.
- Channels `FACEBOOK`, `TIKTOK`, and `MARKETPLACE`.
- Content and approval Java package, V7 migration, backend tests, OpenAPI, ADR, README/project context, and this task record.

### Out of scope

- Gemini, prompts, or any other AI generation.
- Publishing, scheduling, connectors, and notifications.
- Frontend changes.
- Editing an approved content item or forking it into a new revision.

### Potentially affected files or modules

- `backend/src/main/java/vn/omnismart/content/`
- `backend/src/main/java/vn/omnismart/audit/AuditAction.java`
- `backend/src/main/resources/db/migration/V7__content_approval_core.sql`
- `backend/src/test/java/vn/omnismart/content/`
- `backend/src/test/java/vn/omnismart/PostgreSqlSchemaIT.java`
- `docs/openapi/backend-basic.yaml`, `docs/adr/`, `README.md`, and `docs/PROJECT_CONTEXT.md`

## Analysis

### Assumptions

- An Owner may approve their own draft because a beta store may have only one Owner; the actor and reviewer remain auditable.
- The feature branch is based on `chore/setup-project-workflow`; merging or rebasing onto `main` is not authorized.
- Content is plain text with a maximum of 10,000 characters; channel-specific formatting belongs to later work.

### Open questions

- None after the user approved the proposed schema, API, RBAC, state machine, branch, commit, and push scope.

### Risks

- H2 cannot prove every PostgreSQL constraint; the PostgreSQL 17 Testcontainers profile will cover tenant composite keys and checks.
- Product facts may change after content approval; publishing must revalidate source-of-truth fields in a later vibe.
- Content and approval state could drift after partial writes; all workflow transitions will be transactional and protected by the content item optimistic version.

### Edge cases

- Empty/oversized content, archived/missing products, stale versions, invalid transitions, duplicate submission, concurrent review, unauthorized and cross-tenant access, and archived/unconfirmed stores.

## Decision

### Selected solution

`Use one content aggregate package containing ContentItem, immutable ContentVersion, and ContentApproval. Explicit action endpoints enforce the state machine, tenant-scoped repositories prevent resource disclosure, and the item lock version serializes edits and review decisions.`

### Alternatives considered

- Separate content and approval modules were deferred because the first workflow is one transaction and premature separation would create cross-module coupling.
- HTTP `If-Match` was not selected because existing Product mutations use an explicit numeric `version` request field.
- A PostgreSQL-only partial unique index for pending approvals was not selected; item locking plus one approval per submitted version remains portable to H2 tests.

## Implementation plan

1. Add V7 schema and PostgreSQL assertions so tenant and workflow invariants are executable.
2. Implement the content aggregate, repositories, validation, state transitions, locking, authorization, and audit behavior.
3. Expose tenant-scoped REST endpoints and cover success and failure paths with MockMvc tests.
4. Update OpenAPI and backend documentation, then run the complete backend and PostgreSQL verification suites.
5. Review and stage only approved files, commit conventionally, and push the task branch without merging.

## Acceptance criteria

- [x] Owner and Staff can create, edit, list, read, version, and submit manual content inside their store.
- [x] Only Owner can approve or reject an in-review content item.
- [x] Invalid transitions and stale versions return stable `409` errors.
- [x] Rejected content requires a new version before resubmission; approved content is immutable in Vibe 1.
- [x] Every mutation is tenant-scoped and audited without storing content bodies in audit metadata.
- [x] Flyway, OpenAPI, H2 tests, and PostgreSQL tests agree with the implementation.

## Verification plan

- `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify`
- `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify -Ppostgres-it`
- `git diff --check`, full `git diff`, staged-file review, and `git status --short --branch`

## Results

### Implementation result

`Implemented the V7 tenant-safe schema, manual content aggregate and immutable versions, Owner/Staff workflow API, optimistic and Product-row locking, audit events, redacted diagnostics, OpenAPI 0.2.0 contract, ADR, and backend/PostgreSQL tests.`

### Verification evidence

- Baseline `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify` — passed, 58 tests.
- Baseline `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify -Ppostgres-it` — passed, 3 PostgreSQL tests.
- Focused `backend\.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ContentApprovalTests test` — passed, 11 tests.
- Intermediate `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify -Ppostgres-it` — passed, 66 H2 tests and 6 PostgreSQL tests before the final review adjustment.
- Final `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify` — passed, 69 tests, 0 failures/errors/skips.
- Final `backend\.\mvnw.cmd --batch-mode --no-transfer-progress verify -Ppostgres-it` — passed, 69 H2 tests and 6 PostgreSQL 17 tests.
- `git diff --cached --check` — passed with no whitespace errors.
- Staged diff review and secret-pattern scan — 22 approved files only; no secret-like additions detected.

### Remaining issues

- No issue remains within the approved Vibe 1 scope. Publishing must later revalidate Product source-of-truth fields, and approved-content revision remains intentionally deferred.
- The branch is based on `chore/setup-project-workflow`; merge that workflow branch first or retarget/rebase this branch after it reaches `main`.

### Delivery

- **Commit:** `feat: add content approval workflow` (hash reported in handoff)
- **Push:** `origin/feat/content-approval-core` after the task commit
- **Pull request:** `not requested`
