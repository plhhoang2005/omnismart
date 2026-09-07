# Development Backlog

Use this file for a small, repository-local view of work that needs durable context. Product planning remains in `PLAN_PRODUCT_OMNISMART.md` and issue tracking may remain in GitHub.

## Status flow

`BACKLOG -> ANALYZING -> WAITING_APPROVAL -> IMPLEMENTING -> TESTING -> REVIEWING -> DONE`

Move a task to `DONE` only when `docs/DEFINITION_OF_DONE.md` is satisfied. A blocked task stays at its current truthful status and records the blocker in its description or current-task file.

## Tasks

| ID | Task | Description | Priority | Status | Dependencies | Completion criteria |
|---|---|---|---|---|---|---|
| DOC-001 | Reconcile the backend handoff report | Check and correct stale statements about `GEMINI_API_KEY` and the media storage adapter without changing runtime behavior. | Medium | BACKLOG | None | The report agrees with current configuration, source names, and ADRs; documentation-only checks pass. |
| DX-001 | Reassess a root verification command | Add a cross-platform root verification entry point only if repeated use shows that the documented Maven, npm, and Compose commands cause workflow friction. | Low | BACKLOG | Workflow usage evidence | A demonstrated need, approved design, documented behavior, and successful backend/frontend/Compose verification. |

## New task row

```text
| <ID> | <Task name> | <Short description> | High/Medium/Low | BACKLOG | <IDs or None> | <Observable acceptance criteria> |
```
