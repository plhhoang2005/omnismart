# ADR 0005: Tenant-scoped product catalog and temporary-first media

## Status

Accepted

## Context

OmniSmart needs reliable product facts before AI content generation. SKU, price, inventory and promotion data are commercial source-of-truth fields. A stale browser tab, cross-tenant identifier or invalid image must not silently corrupt that source of truth.

## Decision

- Every product and media row carries `store_id`; resource repositories load by both store and resource ID.
- A composite foreign key from `product_media(product_id, store_id)` to
  `product(id, store_id)` makes cross-store media attachment invalid at the database boundary.
- SKU is trimmed, uppercased and unique per store. Price is `NUMERIC(19,2)`. Currency and lifecycle values are enums backed by database checks.
- Product uses JPA `@Version`. Clients must send the observed version for update and archive; stale values return `409 PRODUCT_VERSION_CONFLICT`.
- Archive replaces hard delete and requires the exact normalized SKU as human confirmation.
- `ProductPublicationGuard` is a required archive boundary. The default implementation is safe only while no publishing-job model exists; the publishing phase must replace it with a real active-job query.
- AI code will receive a separate content-drafting boundary. The authenticated catalog mutation endpoints remain the only path for SKU, price and inventory changes, so these values originate from a user action.
- Media storage is behind `MediaStorage`. The current local adapter uses server-generated object keys and can later be replaced by S3-compatible storage.
- Upload writes a temporary object and a `TEMPORARY` database state first. Only content-validated JPEG, PNG or WebP is promoted and changed to `ATTACHED`.
- MIME claims and filenames are ignored for trust decisions. JPEG/PNG are parsed for dimensions; WebP RIFF and VP8 chunk structure are validated. Image size, pixel count and per-product count are bounded.
- Primary media is selected only by an explicit user request. Upload never asks AI to choose or publish an image.
- A scheduled job deletes stale temporary uploads and reconciles old stored objects against database object keys in bounded batches.
- Product create, update, archive, media attachment and primary-image changes are tenant-scoped audit events. Audit details do not contain price or inventory values.
- The optional `postgres-it` Maven profile runs Flyway and tenant-integrity tests against a disposable PostgreSQL 17 Testcontainer. Fast tests continue to use H2.

## Consequences

- Concurrent catalog edits fail visibly instead of overwriting one another.
- Local media is appropriate for development and a single-instance demo. Staging with multiple backend instances must provide a shared `MediaStorage` adapter before rollout.
- Database and filesystem updates cannot form one atomic transaction. The upload service performs compensation on failure, and the cleanup job handles crash-created orphans.
- PostgreSQL compatibility and tenant foreign keys are exercised separately from fast unit/API tests, so database-specific failures are detected before deployment.
- Bulk archive is not exposed yet. When added, it must preview the affected count and require a separate confirmation step.
