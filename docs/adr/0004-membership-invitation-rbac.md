# ADR 0004: Explicit membership invitations and owner-safe RBAC

- Status: Accepted
- Date: 2026-08-20

## Context

Small stores need to add staff without sharing accounts. Adding a user merely because their email domain matches would grant access without consent. Role changes also create a practical race: two Owners could concurrently demote or remove themselves and leave a store without an administrator. Invitation links are bearer credentials and must not become reusable secrets in the database or logs.

## Decision

- Only an authenticated store Owner can list members, create invitations, change roles or revoke membership.
- Invitations target one normalized verified email, expire after a configurable duration and require the recipient to explicitly accept or decline while signed in with that email.
- The API returns a raw token only in the create response for manual delivery. The database stores its SHA-256 hash. List endpoints never return the token, and audit details never contain it.
- Invitation responses lock the invitation row. A terminal invitation cannot be used again.
- A unique nullable `pending_email` key permits only one pending invitation per store/email while preserving invitation history after it reaches a terminal state.
- `OWNER` and `STAFF` are enums in Java and constrained values in PostgreSQL.
- Inviting an Owner, changing any role and revoking a member require the Owner to confirm the exact current store name.
- Role change and revocation lock all membership rows for the store before counting Owners. The operation is rejected if it would remove the final Owner.
- Archived stores retain history but cannot mutate memberships or invitations.
- Invitation and membership mutations append tenant-scoped audit records. Raw tokens are never audit data.

## Consequences

- Email delivery can be added behind an adapter later without changing the invitation lifecycle.
- Manual token delivery is suitable only for development and controlled beta testing.
- The database locks serialize membership administration per store, which favors correctness over maximum write throughput; this is appropriate for small-store workloads.
- Hashing protects tokens in a database disclosure, but possession of the raw token plus access to the invited Google account remains sufficient to respond.
