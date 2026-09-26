# GraphQL API

Source: `backend/src/main/resources/graphql/schema.graphqls`. HTTP POST and WebSocket endpoint: `/graphql`. Protocol:
`graphql-transport-ws`. REST is limited to `/auth/*` and infrastructure.

Dates are ISO `YYYY-MM-DD` strings validated on mutation; timestamps are ISO-8601 UTC strings. IDs are opaque. Explicit
payload/input types and enums are reflected in generated TypeScript documents under `frontend/src/gql`; regenerate with
`npm run codegen` after schema changes. Schema inspection during boot must show no unmapped fields.

Sanitized extension codes: `FORBIDDEN`, `BAD_INPUT`, `CONFLICT`, `LAST_OWNER`, `DUPLICATE_ACCOUNT`, `INTERNAL_ERROR`.
GraphQL parse/schema validation errors use GraphQL's standard error classification. HTTP rejects unauthenticated
requests with 401, failed CSRF/origin with 403, oversized JSON with 413 and rate limits with 429. Depth limit 10,
complexity 250, JSON/WS messages 64 KiB, auth limit 20 requests/minute/IP, general limit 600/minute/IP; WebSocket operations 120/minute/session. Limits are
process-local and use bounded caches. CSRF applies to GraphQL queries as well as mutations because all HTTP operations
use POST.

```graphql
query Home {
  me {
    id
    name
    email
  }
  workspaces {
    id
    name
    role
    projects {
      id
      name
      workspaceId
    }
  }
}
mutation Workspace($input: WorkspaceInput!) {
  createWorkspace(input: $input) {
    workspace {
      id
      name
      role
    }
  }
}
# variables: {"input":{"name":"Product Studio"}}
query Tasks($projectId: ID!, $after: String) {
  tasks(
    projectId: $projectId
    first: 20
    after: $after
    sort: CREATED_ASC
    filter: { status: TODO }
  ) {
    nodes {
      id
      title
      status
      priority
      version
      assignee {
        id
        name
      }
    }
    endCursor
    hasNextPage
  }
}
mutation Edit($input: UpdateTaskInput!) {
  updateTask(input: $input) {
    task {
      id
      title
      version
      updatedAt
    }
  }
}
subscription Changes($workspaceId: ID!) {
  workspaceChanges(workspaceId: $workspaceId) {
    id
    workspaceId
    entityId
    type
    timestamp
    version
  }
}
```

All complete executable UI operations are in `frontend/src/operations.graphql`. Task cursors are bound to project and
sort; reset them when changing filters. Comments show newest first and advance by the last ID (descending); activity advances by the last ID
(descending). First defaults to 20/30 and cannot exceed 50. Access checks happen at the application layer before data
retrieval. Nested records only originate from authorized parent queries; project and user associations are batched.
Event payloads contain IDs, never task descriptions or comment bodies.
