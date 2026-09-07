# ADR 0006: Tenant-scoped manual content approval workflow

## Status

Accepted

## Context

OmniSmart needs a human-reviewed content workflow before AI drafting or publishing is introduced. Staff must be able to prepare channel-ready text without gaining approval authority, and concurrent browser tabs or cross-tenant identifiers must not overwrite or expose another store's work.

## Decision

- The `content` package owns one aggregate made of `ContentItem`, immutable `ContentVersion` rows, and an approval attempt for every submitted version.
- Manual drafts target `FACEBOOK`, `TIKTOK`, or `MARKETPLACE`. Content is plain text limited to 10,000 characters; channel-specific structured formats are deferred.
- The current workflow is `DRAFT -> IN_REVIEW -> APPROVED` or `DRAFT -> IN_REVIEW -> REJECTED -> DRAFT` after a changed version is created.
- Owner and Staff can create, edit, read, list, and submit content. Only an Owner can approve or reject. An Owner may review their own draft so a single-Owner store remains usable.
- Content in review cannot be edited. Rejected content cannot be resubmitted until a changed immutable version exists. Approved content is terminal for this phase.
- Every store-owned query includes `store_id` and the resource ID. Composite foreign keys enforce the same tenant across Product, ContentItem, ContentVersion, and ContentApproval.
- `ContentItem` uses JPA `@Version`; clients send the observed `version` for every edit or transition. A stale request returns `409 CONTENT_VERSION_CONFLICT`.
- Action endpoints (`submit`, `approve`, and `reject`) enforce the state machine instead of accepting arbitrary status patches.
- Product must be active for create, edit, submit, and approve. If it is archived during review, an Owner may still reject the pending content to close the workflow.
- Each mutation creates a tenant-scoped audit event. Audit metadata contains identifiers, channel, and version number, but never the content body or rejection reason.
- AI generation, prompt versions, publishing, scheduling, and approved-content revisions remain separate later decisions.

## Consequences

- Historical content and approval decisions remain traceable without mutable version rows.
- A concurrent edit or review fails visibly instead of silently replacing another user's decision.
- The content item and approval status are updated in one database transaction; PostgreSQL constraints prevent cross-tenant or cross-version approval links.
- Product facts are not snapshotted into content versions. A later publishing boundary must revalidate human-owned SKU, price, inventory, and promotion data before release.
- Supporting edits after approval will require an explicit revision/fork model rather than reopening the approved aggregate silently.
