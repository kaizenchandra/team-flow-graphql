# TeamFlow

A collaborative project/task workspace built with Java 21, Spring Boot 4.1.1, PostgreSQL, schema-first GraphQL and
React. Authentication uses server sessions; live updates use `graphql-transport-ws`.

## Start with Docker

Requires Docker Engine/Compose. From this directory:

```sh
sh scripts/init-env.sh
# Creates .env with a random password once; preserves an existing .env.
docker compose up --build -d --wait
sh scripts/smoke.sh http://localhost:8088
```

Open http://localhost:8088. Register your own account; there are no default accounts or production passwords. Create a
workspace in the sidebar, create a project, and add tasks. Register a second account in another browser/incognito
context; add its email in Workspace settings. Both browsers can edit tasks and comment. Temporarily disconnect one
browser, make a change in the other, and reconnect to see reconciliation.

Stop: `docker compose down` (retains database volume). Never use `down -v` unless intentionally deleting all data.

Keep `.env` for subsequent starts and never commit it. Do not copy `.env.example` over an existing `.env` or change
`DB_PASSWORD` just to restart: PostgreSQL retains the original role password in its persistent volume.
If startup reports `password authentication failed for user "teamflow"`, see
[credential recovery](docs/deployment.md#database-password-mismatch).

## IntelliJ and local development

1. Set project SDK and Maven runner JDK to **21**. Import `backend/pom.xml` as Maven.
2. Run `sh scripts/init-env.sh` to create or retain `.env`, then run `docker compose up -d db`.
3. Run `scripts/dev-backend.sh` with `JAVA_HOME` pointing to JDK 21. Or run `TeamFlowApplication` in IntelliJ with
   `DB_PASSWORD` and `APP_ORIGIN=http://localhost:5173` set in its run configuration. PostgreSQL binds to loopback
   **55432** to avoid occupying a conventional installation on 5432.
4. `cd frontend && npm ci && npm run dev`. Open **http://localhost:5173** (the allowed origin is exact; do not
   substitute 127.0.0.1).
5. Backend: http://localhost:8080/actuator/health. Frontend proxies GraphQL, WebSocket and auth requests to it.

```sh
cd backend
./mvnw verify
cd ../frontend
npm ci
npm run lint
npm test
npm run build
npx playwright install chromium
npm run e2e                     # running dev stack
BASE_URL=http://localhost:8088 npm run e2e  # running Compose stack
```

Use Node 24.8+ and Java 21. Maven Wrapper is pinned to 3.9.11; frontend dependency versions and transitive lockfile are
retained. Docker tests require an accessible Docker socket. On OrbStack:
`export DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock` if automatic discovery fails.

See [requirements](docs/requirements.md), [architecture](docs/architecture.md), [GraphQL](docs/graphql.md), [plan](docs/plan.md), [current evidence](docs/progress.md), [testing](docs/testing.md),
and [deployment/recovery](docs/deployment.md).
