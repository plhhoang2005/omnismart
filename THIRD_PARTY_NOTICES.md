# Third-party notices

This file records third-party software, assets, datasets, models and hosted services used by OmniSmart.

## Application foundation

| Component | Version family | Purpose | License/terms |
|---|---|---|---|
| Spring Boot | 4.1.x | Backend framework | Apache-2.0 |
| Spring Security OAuth2 Client | Managed by Spring Boot | Google OpenID Connect login | Apache-2.0 |
| React | 19.x | Web interface | MIT |
| Vite | 8.x | Frontend toolchain | MIT |
| Vitest | 4.x | Frontend tests | MIT |
| PostgreSQL | 17.x | Relational database | PostgreSQL License |
| Flyway Community | Managed by Spring Boot | Database migrations | Apache-2.0 for applicable community components |
| H2 Database Engine | Managed by Spring Boot | Backend tests | MPL-2.0 or EPL-1.0 |

Exact resolved versions are recorded in `frontend/package-lock.json` and the Maven dependency graph. Before a release candidate is published, maintainers will generate and review a dependency inventory or software bill of materials (SBOM) so transitive dependencies are represented too.

The competition HTML, training PDF and earlier local planning notes used as planning references are intentionally not redistributed because their redistribution terms have not been confirmed.

## Required entry format

| Component | Version | Purpose | Source | License/terms |
|---|---|---|---|---|
| Example only | 0.0.0 | Replace when adopted | https://example.com | SPDX identifier |

Add a reviewed entry in the same pull request that introduces each dependency or asset. API service terms must be recorded separately from open-source dependency licenses.

The implementation follows the official Spring Security OAuth2 Login documentation and uses the official Spring Security sample repository as a design reference. No sample repository or provider token is vendored into OmniSmart.
