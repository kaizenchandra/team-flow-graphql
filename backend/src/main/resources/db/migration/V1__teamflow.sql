create table app_user
(
    id       varchar(36) primary key,
    email    varchar(254) not null unique,
    name     varchar(80)  not null,
    password varchar(100) not null
);
create table workspace
(
    id   varchar(36) primary key,
    name varchar(120) not null
);
create table membership
(
    id           varchar(36) primary key,
    workspace_id varchar(36) not null references workspace (id),
    user_id      varchar(36) not null references app_user (id),
    role         varchar(10) not null check (role in ('OWNER', 'ADMIN', 'MEMBER')),
    unique (workspace_id, user_id)
);
create index membership_user on membership (user_id, workspace_id);
create table project
(
    id           varchar(36) primary key,
    workspace_id varchar(36)  not null references workspace (id),
    name         varchar(120) not null,
    unique (id, workspace_id)
);
create index project_workspace on project (workspace_id, id);
create table task
(
    id            varchar(36) primary key,
    project_id    varchar(36)    not null,
    workspace_id  varchar(36)    not null,
    title         varchar(200)   not null,
    description   varchar(10000) not null,
    status        varchar(20)    not null check (status in ('TODO', 'IN_PROGRESS', 'DONE')),
    priority      varchar(10)    not null check (priority in ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    priority_rank integer        not null,
    assignee_id   varchar(36),
    due_date      date,
    version       bigint         not null default 0,
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null,
    foreign key (project_id, workspace_id) references project (id, workspace_id),
    foreign key (workspace_id, assignee_id) references membership (workspace_id, user_id)
);
create index task_created on task (project_id, created_at, id);
create index task_updated on task (project_id, updated_at desc, id desc);
create index task_priority on task (project_id, priority_rank desc, id desc);
create index task_assignee on task (workspace_id, assignee_id);
create table comment
(
    id         varchar(36) primary key,
    task_id    varchar(36)   not null references task (id) on delete cascade,
    author_id  varchar(36)   not null references app_user (id),
    body       varchar(4000) not null,
    created_at timestamptz   not null
);
create index comment_task on comment (task_id, id);
create table activity
(
    id           varchar(36) primary key,
    workspace_id varchar(36) not null references workspace (id),
    actor_id     varchar(36) not null references app_user (id),
    type         varchar(40) not null,
    entity_id    varchar(36) not null,
    created_at   timestamptz not null
);
create index activity_workspace on activity (workspace_id, id);
