# Architecture

## Context and containers

```mermaid
flowchart LR
  People[Workspace members] --> Browser[React browser application]
  Browser -->|HTTPS: sessions + CSRF| Proxy[Nginx static UI and reverse proxy]
  Browser <-->|WSS: graphql-transport-ws| Proxy
  Proxy --> App[Java 21 / Boot 4.1.1 modular monolith]
  App --> DB[(PostgreSQL 17)]
```

## Modules and dependency direction

```mermaid
flowchart TD
    Transport[GraphQL controllers / auth endpoints] --> Identity[identity: accounts and password encoding]
    Transport --> Workspace[workspace: membership and permissions]
    Transport --> Work[work: projects, tasks, comments]
    Work --> Workspace
    Workspace --> Identity
    Work --> Identity
    Work --> Events[platform: after-commit events and activity]
    Workspace --> Events
    Events --> DB[(JPA / PostgreSQL)]
    Work --> DB
    Workspace --> DB
    Identity --> DB
```

Application services own transactions and permission checks. Controllers adapt validated input records and return API
records; persistence entities are never GraphQL objects. Flat foreign-key identifiers avoid accidental lazy traversal.
User association resolvers and workspace/project nesting batch reads. Tests count SQL for nine workspaces.

## Data model

```mermaid
erDiagram
  APP_USER ||--o{ MEMBERSHIP : joins
  WORKSPACE ||--|{ MEMBERSHIP : contains
  WORKSPACE ||--o{ PROJECT : contains
  PROJECT ||--o{ TASK : contains
  MEMBERSHIP o|--o{ TASK : assigned
  TASK ||--o{ COMMENT : contains
  APP_USER ||--o{ COMMENT : authors
  WORKSPACE ||--o{ ACTIVITY : records
  APP_USER ||--o{ ACTIVITY : acts
```

Foreign keys bind each task to a project/workspace pair and each assignee to a membership in that same workspace. Unique
membership and normalized-email constraints prevent duplicates. Check constraints bound role/status/priority values.
Tasks carry JPA `@Version`, created/updated timestamps, and indexes matching the three sort orders. A workspace row lock
serializes membership and mutation authorization with revocation; this favors correctness at the cost of write
throughput within a busy workspace. Removing a member clears assignments and increments task versions. Deleting tasks
cascades comments but retains activity. Activity/comment IDs include a timestamp prefix plus random tie-breaker;
pagination is stable by ID, including ties within a millisecond.

## Authentication and authorization

```mermaid
sequenceDiagram
    participant B as Browser
    participant S as Spring Security
    participant A as Application service
    participant D as PostgreSQL
    B ->> S: GET /auth/csrf (session token)
    B ->> S: POST /auth/login + CSRF + form credentials
    S ->> D: Load normalized email / BCrypt hash
    S -->> B: Rotated HttpOnly session cookie
    B ->> S: POST /graphql + cookie + CSRF
    S ->> A: Authenticated principal and validated input
    A ->> D: Check workspace membership / role / entity workspace
    A ->> D: Execute authorized transaction
    A -->> B: API payload or sanitized error
```

No wildcard CORS. HTTP Origin, when supplied, must exactly match `APP_ORIGIN`; WebSocket upgrades require it. Sessions
expire after 30 minutes without HTTP access, and logout invalidates them. Socket operations retain a bounded session
reference, recheck session validity and membership before every event and every two seconds, and terminate on failure.
WebSocket activity does not extend session expiry. Server restarts invalidate sessions.

## Mutation and subscription

```mermaid
sequenceDiagram
    participant A as Browser A
    participant S as Work service
    participant D as PostgreSQL
    participant E as In-process publisher
    participant B as Browser B
    A ->> S: Mutation + expected version
    S ->> D: Membership check, row update, activity insert
    D -->> S: Commit successful
    S ->> E: AFTER_COMMIT change event
    E ->> E: Check B session + current membership
    E -->> B: ID / entity / type / timestamp / version
    B ->> S: Refetch active queries
    S -->> B: Authoritative persisted state
    Note over B, S: On reconnect, refetch again; keep at most 512 event IDs
```

A subscriber has a 128-event buffer; overflow terminates that subscription so reconnect/reload can recover. Transport
has no durable replay, no cross-instance distribution, and no exactly-once guarantee. Clients use bounded exponential
reconnect delay (1–15 seconds with jitter, eight retries) and authoritative refetch rather than optimistic mutation
updates. Editing forms retain a version snapshot to expose conflicts without discarding local edits.

## Capacity and availability

One JVM and one PostgreSQL instance; no capacity benchmark or availability SLA is claimed. Hikari defaults and
serialized workspace writes constrain throughput. Rate limits are per-process, per source IP; sessions and deduplication
are memory-resident. The backend container has a 768 MiB memory limit. Before adding replicas, measure workload,
introduce shared session storage, use a transactional outbox plus shared event distribution, add cross-node rate
limiting and subscription revocation, and keep load-balancer WebSocket affinity/timeout settings aligned. PostgreSQL HA
and backups need their own operational plan.

See ADRs for boundaries, security and event delivery decisions.
