# ADR 0003: Tenant-safe store access and explicit onboarding

- Status: Accepted
- Date: 2026-08-20

## Context

Every OmniSmart business resource belongs to a store. Accepting a resource identifier from a browser without proving store membership can expose another tenant's data. A first Google login also needs a usable workspace, but silently treating an inferred store name as user-approved would bypass the human onboarding decision.

## Decision

- `store_member` is the authority for store access. `StoreAuthorizationService` resolves the authenticated Google subject to an application user and then checks membership on the backend.
- Store lookups outside the authenticated user's tenant return `404` so the API does not disclose whether another store exists.
- Store identifiers come from the URL or authenticated context, never from create-request bodies. Unknown JSON fields are rejected.
- First login creates an `ACTIVE` store with `onboarding_completed=false`. An Owner must submit a valid name to complete onboarding.
- Only an Owner may change store configuration. Archiving requires the exact current name and must be separate from a rename.
- There is no hard-delete API in this phase. `ACTIVE` and `ARCHIVED` are the only persisted lifecycle states.
- Future store-owned repositories must query by both resource ID and `store_id`, for example `findByIdAndStoreId`, rather than loading by resource ID and checking afterward.

## Consequences

- Tenant checks are reusable and testable instead of being duplicated in controllers.
- The default store makes first login idempotent while the onboarding flag preserves a human confirmation step.
- Archive is recoverable and reduces accidental data loss.
- Returning `404` for both missing and unauthorized stores makes operational diagnosis less explicit to clients, so internal correlation logs will later be needed.
