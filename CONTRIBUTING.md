# Contributing to OmniSmart

Thank you for contributing to OmniSmart.

## Before starting

1. Search existing issues and pull requests.
2. Create or select an issue with clear acceptance criteria.
3. Discuss large architectural, security or product changes before implementation.

## Branches and commits

- Branch from `main` using `feat/<issue>-<description>`, `fix/<issue>-<description>` or `docs/<issue>-<description>`.
- Keep branches short-lived and focused.
- Use Conventional Commits such as `feat: add Google SSO callback`.
- Never commit credentials, personal data, `.env` files or database dumps.

## Pull requests

- Keep pull requests small enough to review confidently.
- Complete the pull request template.
- Include tests and documentation with the change.
- Add screenshots or a short recording for user-interface changes.
- Wait for required checks and at least one approval before merging.
- Prefer squash merge and delete the branch afterward.

## Definition of done

- Acceptance criteria are met.
- Happy path and relevant failure paths are tested.
- Loading, empty, success and error states are handled in the UI.
- Security, privacy and tenant-isolation effects are considered.
- Documentation and API contracts are current.
- The change deploys to staging without manual database edits.

## Licensing contributions

Unless explicitly stated otherwise, contributions submitted to this repository are licensed under Apache-2.0 according to the project `LICENSE`.

