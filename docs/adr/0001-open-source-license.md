# ADR 0001: Publish OmniSmart under Apache-2.0

- Status: Accepted
- Date: 2026-08-14

## Context

OmniSmart is being developed for an open-source competition and may continue as a commercial product. The repository needs an explicit license that permits inspection, contribution, modification and commercial use while preserving attribution and providing an express patent grant.

## Decision

Code and original documentation contributed to OmniSmart are licensed under Apache License 2.0 unless a file clearly states otherwise.

Third-party dependencies, assets, datasets, models and hosted services retain their own licenses or terms. They must be reviewed and recorded in `THIRD_PARTY_NOTICES.md`; release builds will later include an SPDX or CycloneDX SBOM.

Competition source material whose redistribution rights are unconfirmed is not committed to the public repository.

## Consequences

- Other parties may use, modify and distribute OmniSmart, including commercially, under Apache-2.0 conditions.
- Copyright, license and NOTICE information must remain with redistributions.
- The team cannot revoke Apache-2.0 rights already granted for published versions.
- Every dependency or asset addition requires license review.

