# Execution plan

| Phase | Deliverables and acceptance gate                     | Validation                                                     |
|-------|------------------------------------------------------|----------------------------------------------------------------|
| 0     | Scope/matrix, diagrams, schema, compatible baseline  | Inspect repository; official docs; dependency resolution       |
| 1     | Java 21/Boot 4.1.1, React, PostgreSQL/Flyway, health | Maven package; npm build; start services                       |
| 2     | Sessions, CSRF, identity and workspace roles         | Security and cross-workspace tests                             |
| 3     | Projects/tasks/comments/activity and UI              | GraphQL, persistence, pagination, conflict and component tests |
| 4     | Authorized post-commit subscriptions and recovery    | Two-context Playwright; revocation/reconnect tests             |
| 5     | Limits, observability, risk tests and UI inspection  | Maven verify; lint/typecheck/tests/build; Playwright           |
| 6     | Images/Compose, CI, manual deployment and recovery   | Compose build/up; health and deployed smoke tests              |
| 7     | Acceptance audit and handover                        | Record executed evidence and precise limitations               |

Each gate requires passing evidence or an explicit environmental block; implementation alone does not complete a gate.

## Final gate status

2026-09-27: Phases 0–5 and 7 passed for the local MVP. Phase 6 production images and local Compose deployment passed;
remote execution is blocked only on the target/access/authorization details. See `progress.md` for exact test counts,
evidence, acceptance audit and operational limits.
