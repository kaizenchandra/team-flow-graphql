# Deployment and recovery

## Local Compose

Run `sh scripts/init-env.sh` to create `.env` with a random password only when absent; retain it on subsequent starts. Keep
`.env` mode 600 and outside Git. `APP_ORIGIN=http://localhost:8088`, `COOKIE_SECURE=false` are for local HTTP only. Run
`docker compose up --build -d --wait --wait-timeout 180`, then `sh scripts/smoke.sh`. Nginx serves the compiled frontend
and proxies `/auth` and `/graphql`, including upgrades, with a 75-second read timeout; server keepalives are 15 seconds.
PostgreSQL persists in `teamflow_pgdata`. Web binds loopback 8088; DB binds loopback 55432. Backend is not exposed on
the host. Images run as non-root except PostgreSQL's entrypoint initialization.

## Database password mismatch

`FATAL: password authentication failed for user "teamflow"` (SQLSTATE `28P01`) means the configured password does not
match the role stored in PostgreSQL. Changing `.env` or `POSTGRES_PASSWORD` does not change an existing volume's role
password. The database health check uses an authenticated connection through `db`, so it also detects this mismatch;
`pg_isready` alone does not verify credentials. Restore the original `.env` if available.

For this local Compose database, when the current `.env` password is the intended value, align the database role using
the container's local administrative connection. This changes the role credential and retains all tables and data:

```sh
docker compose up -d db
docker compose exec -T db sh -c 'printf "%s\n%s\n" "$POSTGRES_PASSWORD" "$POSTGRES_PASSWORD" | psql -X -v ON_ERROR_STOP=1 -U teamflow -d teamflow -c "\password teamflow"'
docker compose up -d --wait --wait-timeout 120
sh scripts/smoke.sh http://localhost:8088
```

The `psql` password command conceals the password from command output/history. Do not use `down -v` as a credential
repair: it deletes application data. On a shared/remote database, coordinate credential rotation with its operator.
See [Docker's persistence guidance](https://docs.docker.com/guides/postgresql/immediate-setup-and-data-persistence/).

## Single Linux host / TLS

Provide Docker/Compose, disk space/monitoring, DNS and TLS termination. Put an approved host reverse proxy in front of
`127.0.0.1:8088`, set `APP_ORIGIN=https://your-domain`, `COOKIE_SECURE=true`, `PUBLIC_SCHEME=https`. Preserve Host,
Upgrade, Connection headers
and idle timeout >75 seconds. Do not expose backend or database publicly. Set a 64 KiB request body limit at the outer
proxy and enable HSTS there after TLS works. No TLS certificates or public host are provisioned by this project.

For example, a host Caddy site can use:

```
teamflow.example.com {
  reverse_proxy 127.0.0.1:8088
}
```

Use your real domain and verified DNS; Caddy handles TLS and WebSocket upgrades. Cookie Secure is explicitly configured
rather than inferred from inner HTTP transport. Secrets may be injected by a host secret manager into an owner-readable
environment file (not baked into images).

## CI and manual deployment

`.github/workflows/ci.yml` verifies Java, frontend and the Compose browser workflow. Deployment is a separate manual
workflow and requires a self-hosted Linux runner labelled `teamflow`, a protected `production` environment, and
`/etc/teamflow/runtime.env`. Configure runner permissions narrowly: Docker access is privileged. The workflow builds the
selected commit, tags it with that commit SHA, starts project `teamflow`, and runs local smoke checks. First execute CI
for that same commit. No remote deployment happens merely by writing these files or pushing application code.

## Migrations and rollout

Back up before upgrades. Flyway runs on backend startup and fails deployment if migrations fail. Never edit a shared
applied migration. Use additive, backward-compatible migrations and expand/contract releases. Wait for database and
backend health before exposing a new UI. Run smoke checks against both loopback and the real HTTPS domain, then run a
two-browser scenario. Watch container health, logs and database disk usage.

## Backup and restore

Backup (write only to a protected, encrypted/off-host destination in real operations):

```sh
umask 077
docker compose exec -T db pg_dump -U teamflow -d teamflow -Fc > teamflow-backup.dump
```

Regularly test restoration into a separate database/isolated Compose project. For an explicitly approved restore into
the application database, first stop web/backend, retain the current backup, then:

```sh
docker compose stop web backend
docker compose exec -T db pg_restore -U teamflow -d teamflow --clean --if-exists < teamflow-backup.dump
docker compose up -d --wait
sh scripts/smoke.sh
```

The restore command replaces database objects and data; execute it only for the intended environment and backup. Recheck
migration version after restore. Document backup frequency, retention, restore drills and recovery objectives for the
deployed service.

## Rollback

Retain previous images before deploying; do not prune them blindly. Set `IMAGE_TAG` to the previous verified commit and
run `docker compose up -d --no-build --wait`. A compatible older application may run against additive migrations;
destructive changes may require forward repair or a coordinated database restore that loses newer writes. Do not run a
schema downgrade implicitly. Session state is in memory, so a backend restart requires sign-in again.

## Observability and external blockers

`PUBLIC_SCHEME` is an operator-controlled value (`http` locally, `https` with TLS). Nginx renders it into the forwarding
configuration at startup and does not derive it from client headers. The proxy intentionally does not trust arbitrary
client X-Forwarded-For values, so per-IP limits aggregate clients behind the proxy. Before serving larger teams,
configure a trusted proxy chain and dedicated edge rate limiting.

Health endpoints are public and return sanitized status. Prometheus metrics are authenticated and not exposed by the
Nginx public route. For production scraping, define a dedicated internal authentication/monitoring integration.
Containers emit ECS JSON logs with per-request correlation IDs; no request bodies, credentials or session IDs are logged
by application code.

No host, domain, deployment runner or remote authorization was supplied. Remote deployment is therefore blocked on those
external details. Consult `docs/progress.md` for the actual local deployment status.

Local execution was verified at http://localhost:8088 with all three containers healthy, passing smoke checks and
production browser tests. Remote HTTPS configuration is prepared but remains unverified until a target/domain is
supplied.
