# Delivery checkpoint — 2026-09-27

## Startup incident resolved — 2026-09-27

The backend restart loop was caused by SQLSTATE `28P01`: the configured database password no longer matched the
`teamflow` role in the persistent PostgreSQL volume. Both containers had the same configured value; a connection
through `db` failed password authentication. Localhost connections used trust authentication and did not detect it.
Aligned the local database role with the current configured password through `psql`'s concealed password command.
No volume, schema or application records were removed: counts remained 27 users and 12 tasks.

Added `scripts/init-env.sh` (random password on first creation, mode 0600, preserves existing configuration), updated
README/recovery instructions, and replaced `pg_isready` with an authenticated database health check.
Verified shell syntax, first creation and repeated initialization in a temporary directory, unchanged workspace `.env`,
authenticated database connectivity, all three Compose services healthy, and passing health/frontend/CSRF smoke checks
at http://localhost:8088. Application code was unchanged; the full application test suites were not rerun for this
configuration repair. Existing unrelated workspace edits were preserved.

## Outcome

TeamFlow is implemented and locally deployed at **http://localhost:8088**. PostgreSQL, backend and web containers are
running and healthy. The final application images, proxy configuration and real browser workflows were executed
successfully. Temporary development processes were stopped; Compose remains running.

Phases 0–5 and 7 are complete for the local MVP. Phase 6 is locally verified; remote deployment remains externally
blocked. The workspace originally contained only AGENTS.md and IntelliJ metadata. An initial local Git commit was
created during implementation and has been preserved; the verified delivery changes are recorded as a follow-up local
commit. No push or remote deployment was performed.

## Final verification evidence

| Check                                                       | Executed result                                                                                                      |
|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Java 21 Maven Wrapper `./mvnw -B verify`                    | **14 passed**, 0 failed/errors/skipped: 4 rules/session tests and 10 PostgreSQL Testcontainers integration tests     |
| PostgreSQL 17.6 / Flyway / Hibernate                        | Migration applied in disposable PostgreSQL; schema validation passed; persistent Compose database healthy            |
| Frontend ESLint                                             | Passed                                                                                                               |
| Frontend Vitest                                             | **5 passed** across identity/error UI and bounded event deduplication                                                |
| GraphQL Code Generator / TypeScript / Vite production build | Passed with pinned dependencies and package-lock.json                                                                |
| Production Compose Playwright                               | **2 passed** (final run 8.7 seconds)                                                                                 |
| Production Docker builds                                    | Backend and frontend images built successfully                                                                       |
| Compose readiness                                           | db, backend, web all healthy; web loopback 8088, database loopback 55432                                             |
| Deployment smoke                                            | Health UP, compiled frontend and CSRF endpoint passed at http://localhost:8088                                       |
| UI inspection                                               | Desktop and 390px mobile screenshots inspected; no horizontal overflow; explicit labels and keyboard Escape verified |
| GitHub Actions                                              | Workflows prepared; not executed on GitHub                                                                           |
| Remote deployment / public HTTPS                            | Not executed: no authorized host/domain/runner/access method supplied                                                |

Reports: `backend/target/surefire-reports`, `frontend/playwright-report`, `frontend/test-results`. Retained visual
evidence: `docs/screenshots/dashboard-desktop.png` and `dashboard-mobile.png`.

## Acceptance audit

| Requirement                           | Implementation and evidence                                                                                                                                                 |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Registration/login/logout/profile     | REST sessions/BCrypt/CSRF and real UI; browser registration, sign-out and sign-in; HTTP negative tests                                                                      |
| Roles/workspace isolation/final owner | Application-layer matrix, refreshed membership after workspace lock, database constraints; rule tests, cross-workspace tests, concurrent demotion test                      |
| Workspace/project/member workflows    | Connected navigation/forms/settings; browser creates workspace/project, adds and removes another registered user                                                            |
| Tasks/assignee/dates/version conflict | GraphQL service and responsive forms; persistence, assignment validation/cleanup, stale-version tests; browser edit/conflict/delete                                         |
| Filtering/sorting/pagination          | Validated enums and 1–50 keyset pages with ID tie-breaker; all three sorts paginated in integration tests; browser status/priority filters and sort                         |
| Comments/activity                     | Persisted transactionally, bounded ID pagination, newest comments first; integration assertions and cross-browser comment/activity checks                                   |
| Real-time / transaction safety        | Actual graphql-transport-ws; after-commit events; rollback does not emit; second browser receives task/comment updates                                                      |
| Reconnect / duplicate handling        | Forced GraphQL socket close while offline; missed task absent until reconnect then refetched; bounded deduplication unit tests                                              |
| Subscription/active socket security   | Unauthorized/revoked membership and invalid/expired sessions tested; raw WebSocket test denies non-member subscription and a query after logout                             |
| Resource limits / query behavior      | JSON and WS size limits, depth/complexity limits, bounded rate caches; oversized/complex query tests; nested nine-workspace query uses at most five SQL statements          |
| UX / accessibility                    | Loading/empty/error/success states, explicit field labels, focus trap, Escape, responsive layout; component/browser checks and screenshots                                  |
| Operations / delivery                 | Non-root application images, persistent DB, readiness, structured logs/correlation IDs, metrics, reverse proxy, CI/manual deployment, backup/restore/rollback documentation |

## Resolved execution issues

- Existing host port 5432 was preserved; TeamFlow uses 55432.
- Code Generator v6 enum configuration and GraphQlTester API usage were corrected against installed APIs.
- Explicit textarea label associations fixed a browser interaction defect.
- Production WebSocket 403 was traced to forwarded host handling and corrected; production wire tests pass.
- A truncated Maven Central download was retried with Docker dependency caching; the final build passed.
- An automatic review rejected deriving the public scheme from an untrusted header. The accepted implementation uses
  operator-configured `PUBLIC_SCHEME` and was rebuilt and verified.

## Limitations and next action

Single backend instance, in-memory sessions/rate limits/event transport; no durable event replay or cross-instance
distribution, no exactly-once claim, and no measured capacity/SLA. Rate limits aggregate clients behind the bundled
proxy until a trusted edge rate-limit design is configured. Restarting the backend requires sign-in. Pagination restarts
from authoritative state after live refetch. TLS, remote operations, restore drills and hosted CI have not been
verified.

Remote deployment needs the intended Linux host/domain, an access method or configured `teamflow` self-hosted runner,
and authorization for that target. This information was requested. Until supplied, use the verified local deployment and
follow `docs/deployment.md` for host preparation. No local implementation blockers remain.
