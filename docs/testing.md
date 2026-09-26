# Verification

Run Java 21 `backend/./mvnw verify` with Docker available. `IntegrationTest` starts disposable PostgreSQL 17.6 using
Testcontainers, applies Flyway and validates JPA mappings. Tests do not substitute H2. Rules tests exercise role
decisions, final-owner rules and page bounds. Integration tests cover persistence, isolation, assignment cleanup,
competing owner changes, stale versions, GraphQL error codes, HTTP security, and bounded nested-query SQL counts.

Frontend: `npm ci`, `npm run lint`, `npm test`, `npm run build`. Generated operations are schema-validated during the
build. Component tests cover registration constraints, recoverable failed login and conflict feedback.

Start either the dev stack or Compose, install Chromium with `npx playwright install chromium`, then run `npm run e2e`
(dev) or `BASE_URL=http://localhost:8088 npm run e2e` (Compose). Browser tests use independent contexts and unique
accounts, execute the real API, verify task/comment synchronization, stale edits, offline reconnect recovery, membership
revocation and logout. Assertions wait for behavior with bounded deadlines. These tests create persistent test records;
run against a development/test environment only.

Reports: backend `target/surefire-reports`, frontend `playwright-report` and `test-results` (including desktop/mobile
screenshots). CI runs the same essential checks and builds/runs production images before browser tests. Only the
progress record states which checks actually ran; test files and workflow definitions are not evidence of execution.
