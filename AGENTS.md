# OmniSmart Agent Working Agreement

This file applies to the entire repository. It defines the minimum workflow for Codex and other coding agents. Repository documentation and the user's approved request remain authoritative; when instructions conflict, stop and ask before making a material change.

## Required workflow

Every development request follows this sequence:

`UNDERSTAND -> ANALYZE -> PROPOSE -> WAIT FOR APPROVAL -> PLAN -> IMPLEMENT -> VERIFY -> REVIEW -> COMMIT -> PUSH -> HAND OFF`

Do not create a branch, edit files, commit, or push until the proposed scope and plan have been approved. A later approval covers only the files and changes described in that proposal.

## Before coding

1. Read the relevant parts of `README.md`, `docs/PROJECT_CONTEXT.md`, `docs/WORKFLOW.md`, `docs/DEFINITION_OF_DONE.md`, and `tasks/CURRENT_TASK.md`.
2. Inspect only the source, configuration, migrations, API contract, and tests needed for the request.
3. Run `git status --short --branch`; identify the current branch, upstream, and pre-existing changes.
4. Restate the goal, acceptance criteria, in-scope work, and out-of-scope work.
5. Record facts separately from assumptions and unresolved questions. Never invent missing project information.
6. Propose the recommended approach, useful alternatives, affected files, verification commands, risks, and edge cases.
7. Explain worthwhile simplifications or optimizations before implementation.
8. Divide the work into small steps with an observable result for each step.
9. Wait for approval before implementation. Ask again if new information would materially expand the approved scope.

## While coding

- Work on a short-lived branch, never directly on `main` or a release branch.
- Preserve user changes. Do not stash, discard, overwrite, rebase, or rewrite history without explicit permission.
- Change only files required by the approved task. Keep changes small and reviewable.
- Follow `.editorconfig` and existing package-by-domain conventions.
- Do not change the main architecture or business rules without approval.
- Validate untrusted input and produce clear, stable error behavior.
- Never log, expose, or hard-code credentials, tokens, passwords, invitation secrets, or personal data.
- For store-owned resources, enforce tenant membership and query by both `store_id` and the resource ID.
- Check authentication, authorization, CSRF, tenant isolation, and audit requirements whenever relevant.
- Keep source-of-truth commercial fields such as SKU, price, inventory, and promotions human supplied.
- Add or update meaningful tests for changed behavior, including relevant failure paths. Never weaken production code or tests merely to make a check pass.
- Update OpenAPI and documentation when externally visible behavior changes.

## Changes requiring explicit approval

Ask before any of the following, even when they appear useful:

- changing the main architecture or module boundaries;
- adding, removing, or upgrading a dependency;
- changing the database schema or adding a migration;
- editing an applied migration or performing a data-loss-prone migration;
- changing an API contract or introducing a breaking change;
- changing authentication, authorization, tenant isolation, or session behavior;
- deleting or renaming an important file;
- changing production, deployment, release, or GitHub Actions configuration;
- performing an operation that may delete data;
- merging into `main`, creating a release, or deploying an environment.

## Verification and review

Select checks in proportion to the affected area. The canonical commands are in `docs/WORKFLOW.md`.

Before reporting completion:

1. Inspect `git diff`, `git diff --check`, and `git status`.
2. Run the relevant build, tests, lint, type checks, Compose validation, and PostgreSQL integration tests.
3. Exercise both the successful flow and relevant failures: empty or malformed input, duplicates, missing resources, unauthorized access, retries, concurrency, large input, database/external-service failure, and regression risk.
4. Review logic, security, tenant isolation, performance, maintainability, compatibility, test coverage, and accidental changes.
5. Check the diff for secrets, credentials, personal data, generated files, and unrelated edits.
6. If a check cannot run, report the exact check, reason, impact, and safe command for the user to run.
7. Claim completion only with verification evidence and only when `docs/DEFINITION_OF_DONE.md` is satisfied.

## Git and delivery

- Use the branch and Conventional Commit message approved for the task.
- Stage explicit task files; do not use `git add .` when unrelated changes exist.
- Never force-push or push directly to `main`.
- Do not create or merge a pull request unless requested.
- The handoff must state: work completed, changed files, checks and results, edge cases, remaining risks, branch, commit hash, push status, reproduction commands, and a branch or pull-request link when available.
