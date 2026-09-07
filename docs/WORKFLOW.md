# Development Workflow

Use this lightweight workflow for every change. `AGENTS.md` contains the mandatory agent rules; `docs/DEFINITION_OF_DONE.md` defines completion.

## Lifecycle

`BACKLOG -> ANALYZING -> WAITING_APPROVAL -> IMPLEMENTING -> TESTING -> REVIEWING -> DONE`

1. **Receive:** record the request and acceptance criteria in `tasks/CURRENT_TASK.md`.
2. **Understand:** read project context and the minimum relevant code, tests, configuration, API, and migration files.
3. **Analyze:** identify the real need, scope, assumptions, edge cases, and effects on code, API, data, security, and existing behavior.
4. **Propose:** recommend a solution and list alternatives when useful, affected files, checks, risks, and optimizations.
5. **Wait for approval:** do not create a branch or edit files before approval. Ask again for architecture, dependency, schema, API, security, production, destructive, or expanded-scope changes.
6. **Plan:** split approved work into small, verifiable steps and update `tasks/CURRENT_TASK.md`.
7. **Branch:** recheck Git state and create a short-lived branch from the agreed base. Preserve all pre-existing user changes.
8. **Implement:** make only approved, focused changes and verify important steps as work proceeds.
9. **Test:** run checks selected from the matrix below and exercise relevant success and failure paths.
10. **Review:** inspect the entire diff for logic, security, tenant isolation, performance, compatibility, secrets, generated files, and unintended changes.
11. **Commit:** stage explicit task files and use a Conventional Commit message.
12. **Push:** push only the task branch without force. Keep a failed push as a local commit and report the exact error.
13. **Hand off:** report changed files, checks, results, edge cases, risks, branch, commit, push state, reproduction commands, and a GitHub link.
14. **Merge:** merge only when the user explicitly requests it and repository review/check requirements have passed.

## Verification matrix

Run checks from the repository root unless the command changes directory. Do not use production credentials or production data.

| Affected area | Required checks |
|---|---|
| Documentation only | `git diff --check`; validate commands, links, paths, consistency, and absence of secrets |
| Backend code or configuration | `cd backend`; `.\mvnw.cmd --batch-mode verify` |
| Migrations or PostgreSQL behavior | Backend verify plus `.\mvnw.cmd --batch-mode verify -Ppostgres-it` with Docker |
| Frontend code or configuration | `cd frontend`; `npm run lint`; `npm test -- --run`; `npm run build` |
| Compose configuration | `docker compose --env-file .env.example -f infra/compose.yaml config --quiet` |
| API behavior | Relevant backend tests plus comparison with `docs/openapi/backend-basic.yaml` |
| Auth, RBAC, or tenant behavior | Relevant success, unauthenticated, unauthorized, cross-tenant, CSRF, and regression tests |

Broader checks are encouraged when shared behavior may be affected. Do not add dependencies merely to introduce another check.

If a check cannot run, record its command, the reason, the resulting confidence gap, and how another developer can run it. A skipped check is not a passing check.

## Edge-case selection

Select cases relevant to the task rather than creating meaningless tests for every category:

- empty, missing, malformed, oversized, or duplicate input;
- missing or archived resources;
- unauthenticated, unauthorized, and cross-tenant access;
- replayed or repeated requests and idempotency behavior;
- stale versions and concurrent updates;
- database, filesystem, network, or external-service failure;
- cleanup/compensation after partial failure;
- regression of established API and UI behavior.

## Final review and Git safety

Before commit:

```powershell
git status --short --branch
git diff --check
git diff
```

- Confirm every staged file belongs to the approved task.
- Do not remove unrelated user changes or ignored local configuration.
- Do not stage `.env`, credentials, build output, logs, database data, or temporary files.
- Never use force push, rewrite shared history, or push directly to `main`.
- Do not automatically create or merge a pull request.
