# Changelog

All notable changes to OmniSmart will be documented in this file.

The format is based on Keep a Changelog, and the project uses Semantic Versioning during development.

## [Unreleased]

### Added

- Product delivery plan.
- Open-source repository governance foundation.
- Contribution, security and third-party notice policies.
- Initial GitHub Actions and Dependabot configuration.
- Buildable Spring Boot and React application foundations.
- PostgreSQL local infrastructure and a Flyway identity baseline.
- Public system-status endpoint plus frontend and backend tests.
- Google OpenID Connect login with HttpOnly server sessions and CSRF-protected logout.
- Automatic first-store ownership plus Owner and Staff authorization checks.
- Tenant-safe Store API with explicit onboarding confirmation and archive lifecycle.
- Per-client OAuth login/callback rate limiting and separate local/staging cookie profiles.
- Owner-managed member listing, role changes and recoverable membership revocation safeguards.
- Expiring, single-use membership invitations with explicit recipient acceptance or decline.
- Tenant-scoped audit records for invitation and membership changes.
- Tenant-scoped product CRUD with exact prices, SKU normalization, search, archive and optimistic locking.
- Temporary-first JPEG/PNG/WebP upload through a replaceable storage adapter, explicit primary-image selection and bounded orphan cleanup.
- Product and media audit events plus database constraints for catalog invariants.
- Operational-store guard that blocks catalog and invitation mutations until onboarding is confirmed.
- Stable API error responses with request correlation IDs for controller and security failures.
- Owner-confirmed invitation revocation and tenant-safe product-image deletion with audit records.
- A committed OpenAPI contract for the non-AI backend endpoints and fail-fast staging/production configuration.
- PostgreSQL Testcontainers integration tests for Flyway, tenant-scoped SKU uniqueness and cross-store media isolation.
- A composite product/media tenant foreign key that blocks cross-store references at the database boundary.
