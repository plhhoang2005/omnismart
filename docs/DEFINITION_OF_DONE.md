# Definition of Done

A task is `DONE` only when every applicable item below is satisfied. Mark an item not applicable only with a short reason in `tasks/CURRENT_TASK.md`.

## Scope and behavior

- [ ] The approved acceptance criteria are met without unapproved scope expansion.
- [ ] Existing architecture, domain rules, API contracts, and data semantics are preserved unless their change was explicitly approved.
- [ ] Success and relevant failure paths behave clearly and consistently.
- [ ] Loading, empty, success, and error states are handled for affected UI behavior.
- [ ] No known critical or high-severity defect remains in the changed behavior.

## Code quality

- [ ] Changes follow `.editorconfig` and established Java/TypeScript conventions.
- [ ] Input is validated at the appropriate boundary and errors do not expose internals.
- [ ] Duplicate requests, stale updates, concurrency, cleanup, and external failures are handled when relevant.
- [ ] The implementation remains focused, maintainable, and proportionate to the MVP.
- [ ] Old behavior or tests were not removed merely to make verification pass.

## Security and data

- [ ] No secret, credential, token, password, database dump, personal data, or sensitive value appears in source, logs, fixtures, documentation, or the diff.
- [ ] Authentication, authorization, CSRF, tenant isolation, and audit effects were reviewed when relevant.
- [ ] Store-owned reads and writes are tenant scoped by both store and resource identity.
- [ ] Destructive behavior is recoverable or separately confirmed where the product requires it.
- [ ] Database changes use a new reviewed Flyway migration and do not edit an applied migration.
- [ ] AI behavior cannot silently change human-owned commercial facts or bypass human approval.

## Verification

- [ ] The relevant backend build/tests pass.
- [ ] PostgreSQL integration tests pass for migrations or PostgreSQL-specific behavior.
- [ ] Frontend lint, tests, and build pass for frontend changes.
- [ ] Compose validation passes for infrastructure changes.
- [ ] API behavior and `docs/openapi/backend-basic.yaml` agree when the API is affected.
- [ ] Regression risks and meaningful edge cases are covered by automated tests or documented manual evidence.
- [ ] Any check that could not run has its reason, impact, and reproduction command documented; it is not reported as passed.

## Review, documentation, and delivery

- [ ] `git diff --check`, the full diff, and the staged file list were reviewed.
- [ ] Generated files and unrelated user changes are excluded from the commit.
- [ ] Documentation, API contracts, ADRs, and configuration examples are updated when behavior or decisions change.
- [ ] The branch is separate from `main` and the commit follows Conventional Commits.
- [ ] The handoff reports changed files, verification evidence, edge cases, remaining risks, branch, commit hash, push status, and reproduction steps.
- [ ] Merge, release, and deployment have not been performed without explicit authorization.
