# TeamFlow MVP

Stories and acceptance criteria (AC):

1. Identity: register with a unique normalized email and 12–72 character password; login, view profile, logout; invalid
   credentials receive a generic error, writes require CSRF, sessions expire.
2. Workspaces: create/select a workspace; registered users can be added and roles changed/removed according to the
   matrix. Concurrent changes cannot remove the final owner.
3. Projects: members see only projects belonging to accessible workspaces; managers create projects.
4. Tasks: members create/edit/delete tasks with title, description, status, priority, optional member assignee and due
   date. Stale versions return CONFLICT. Filters, sorting and keyset pagination are bounded to 50.
5. Collaboration: members add/read comments and activity; committed task/comment changes appear in another browser.
   Events contain IDs, timestamps and versions. Reconnection reloads persisted state; duplicate events are harmless;
   logout/revocation prevents further restricted delivery.
6. UX: responsive dashboard, project/task detail, settings, labeled forms, loading/empty/error/success states, keyboard
   focus and recoverable conflicts.
7. Operations: clean builds, PostgreSQL migrations, health/metrics, same-origin proxy, Docker deployment, CI and
   recovery instructions.

## Permissions matrix

| Action                                                     | OWNER | ADMIN | MEMBER | Non-member |
|------------------------------------------------------------|-------|-------|--------|------------|
| Read workspace/projects/tasks/comments/activity; subscribe | Yes   | Yes   | Yes    | No         |
| Create/update/delete tasks; add comments                   | Yes   | Yes   | Yes    | No         |
| Create projects                                            | Yes   | Yes   | No     | No         |
| Add/change/remove MEMBER or ADMIN                          | Yes   | Yes   | No     | No         |
| Add/change/remove OWNER                                    | Yes   | No    | No     | No         |
| Demote/remove final owner                                  | No    | No    | No     | No         |

Any authenticated user can create a workspace and becomes its owner. Admins cannot modify owners or grant OWNER. Task
deletion removes its comments, but activity remains. Membership removal clears task assignments. No self-registration
into workspaces.

## Optional backlog

Project archiving, search, notifications, drag/drop, richer profiles, durable event replay and multiple backend
instances. Billing, AI, uploads, integrations and SSO are excluded.
