package com.teamflow.work;

import com.teamflow.identity.IdentityService;
import com.teamflow.platform.*;
import com.teamflow.platform.Api.*;
import com.teamflow.workspace.WorkspaceService;
import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkService {

    private final EntityManager em;
    private final WorkspaceService spaces;
    private final IdentityService identity;
    private final Events events;

    public WorkService(
            EntityManager em,
            WorkspaceService spaces,
            IdentityService identity,
            Events events
    ) {
        this.em = em;
        this.spaces = spaces;
        this.identity = identity;
        this.events = events;
    }

    public static int pageSize(int n) {
        if (n < 1 || n > 50) throw Problem.invalid(
                "Page size must be between 1 and 50."
        );
        return n;
    }

    private static Project view(ProjectEntity p) {
        return new Project(p.id, p.workspaceId, p.name);
    }

    public static Task view(TaskEntity t) {
        return new Task(
                t.id,
                t.projectId,
                t.title,
                t.description,
                t.status,
                t.priority,
                t.assigneeId,
                t.dueDate == null ? null : t.dueDate.toString(),
                t.version,
                t.createdAt.toString(),
                t.updatedAt.toString()
        );
    }

    private ProjectEntity project(String email, String id) {
        var p = em.find(ProjectEntity.class, id);
        if (p == null) throw Problem.forbidden();
        spaces.require(email, p.workspaceId);
        return p;
    }

    private TaskEntity entity(String email, String id) {
        var t = em.find(TaskEntity.class, id);
        if (t == null) throw Problem.forbidden();
        spaces.require(email, t.workspaceId);
        return t;
    }

    public List<Project> projects(String email, String wid) {
        spaces.require(email, wid);
        return em
                .createQuery(
                        "from ProjectEntity where workspaceId=:w order by name,id",
                        ProjectEntity.class
                )
                .setParameter("w", wid)
                .getResultList()
                .stream()
                .map(WorkService::view)
                .toList();
    }

    public Map<Workspace, List<Project>> projectsBatch(
            String email,
            List<Workspace> workspaces
    ) {
        var allowed = spaces
                .list(email)
                .stream()
                .map(Workspace::id)
                .collect(java.util.stream.Collectors.toSet());
        if (
                workspaces.stream().anyMatch(w -> !allowed.contains(w.id()))
        ) throw Problem.forbidden();
        var result = new HashMap<Workspace, List<Project>>();
        workspaces.forEach(w -> result.put(w, new ArrayList<>()));
        if (workspaces.isEmpty()) return result;
        var byId = new HashMap<String, Workspace>();
        workspaces.forEach(w -> byId.put(w.id(), w));
        em.createQuery(
                        "from ProjectEntity where workspaceId in :ids order by name,id",
                        ProjectEntity.class
                )
                .setParameter("ids", byId.keySet())
                .getResultList()
                .forEach(p -> result.get(byId.get(p.workspaceId)).add(view(p)));
        return result;
    }

    public Project createProject(String email, ProjectInput in) {
        spaces.requireManager(email, in.workspaceId());
        spaces.lock(in.workspaceId());
        spaces.requireManager(email, in.workspaceId());
        var p = new ProjectEntity();
        p.workspaceId = in.workspaceId();
        p.name = in.name().trim();
        em.persist(p);
        events.record(
                p.workspaceId,
                p.id,
                "PROJECT_CREATED",
                identity.me(email).id(),
                null
        );
        return view(p);
    }

    public Task task(String email, String id) {
        return view(entity(email, id));
    }

    private void fields(
            TaskEntity t,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            String assignee,
            String due
    ) {
        spaces.validateAssignee(t.workspaceId, assignee);
        t.title = title.trim();
        t.description = description;
        t.status = status;
        t.priority = priority;
        t.priorityRank = priority.ordinal();
        t.assigneeId = assignee;
        try {
            t.dueDate = due == null || due.isBlank() ? null : LocalDate.parse(due);
        } catch (Exception e) {
            throw Problem.invalid("Due date must be YYYY-MM-DD.");
        }
        t.updatedAt = Instant.now();
    }

    public Task createTask(String email, TaskInput in) {
        var p = project(email, in.projectId());
        spaces.lock(p.workspaceId);
        spaces.require(email, p.workspaceId);
        var t = new TaskEntity();
        t.workspaceId = p.workspaceId;
        t.projectId = p.id;
        t.createdAt = Instant.now();
        fields(
                t,
                in.title(),
                in.description(),
                in.status(),
                in.priority(),
                in.assigneeId(),
                in.dueDate()
        );
        em.persist(t);
        em.flush();
        events.record(
                t.workspaceId,
                t.id,
                "TASK_CREATED",
                identity.me(email).id(),
                t.version
        );
        return view(t);
    }

    public Task updateTask(String email, UpdateTaskInput in) {
        var t = entity(email, in.id());
        spaces.lock(t.workspaceId);
        spaces.require(email, t.workspaceId);
        em.refresh(t);
        if (t.version != in.version()) throw Problem.conflict();
        fields(
                t,
                in.title(),
                in.description(),
                in.status(),
                in.priority(),
                in.assigneeId(),
                in.dueDate()
        );
        em.flush();
        events.record(
                t.workspaceId,
                t.id,
                "TASK_UPDATED",
                identity.me(email).id(),
                t.version
        );
        return view(t);
    }

    public String deleteTask(String email, DeleteTaskInput in) {
        var t = entity(email, in.id());
        spaces.lock(t.workspaceId);
        spaces.require(email, t.workspaceId);
        em.refresh(t);
        if (t.version != in.version()) throw Problem.conflict();
        em.remove(t);
        em.flush();
        events.record(
                t.workspaceId,
                t.id,
                "TASK_DELETED",
                identity.me(email).id(),
                t.version
        );
        return t.id;
    }

    public TaskPage tasks(
            String email,
            String pid,
            TaskFilter filter,
            TaskSort sort,
            int first,
            String after
    ) {
        project(email, pid);
        pageSize(first);
        if (sort == null) sort = TaskSort.CREATED_ASC;
        String field = switch (sort) {
            case CREATED_ASC -> "createdAt";
            case UPDATED_DESC -> "updatedAt";
            case PRIORITY_DESC -> "priorityRank";
        };
        boolean asc = sort == TaskSort.CREATED_ASC;
        var params = new HashMap<String, Object>();
        params.put("p", pid);
        StringBuilder where = new StringBuilder(
                "from TaskEntity t where t.projectId=:p"
        );
        if (filter != null) {
            if (filter.status() != null) {
                where.append(" and t.status=:s");
                params.put("s", filter.status());
            }
            if (filter.priority() != null) {
                where.append(" and t.priority=:r");
                params.put("r", filter.priority());
            }
            if (filter.assigneeId() != null) {
                where.append(" and t.assigneeId=:a");
                params.put("a", filter.assigneeId());
            }
        }
        if (after != null) {
            try {
                var bits = new String(
                        Base64.getUrlDecoder().decode(after),
                        StandardCharsets.UTF_8
                ).split("\\|", -1);
                if (
                        bits.length != 4 ||
                                !bits[0].equals(pid) ||
                                !bits[1].equals(sort.name())
                ) throw new IllegalArgumentException();
                int split = bits[2].lastIndexOf('~');
                var key = bits[2].substring(0, split);
                var id = bits[2].substring(split + 1);
                UUID.fromString(id);
                params.put(
                        "key",
                        sort == TaskSort.PRIORITY_DESC
                                ? Integer.valueOf(key)
                                : Instant.parse(key)
                );
                params.put("id", id);
                String op = asc ? ">" : "<";
                where.append(
                        " and (t." +
                                field +
                                op +
                                ":key or (t." +
                                field +
                                "=:key and t.id" +
                                op +
                                ":id))"
                );
            } catch (Exception e) {
                throw Problem.invalid("Invalid cursor for this project and sort.");
            }
        }
        var q = em.createQuery(
                where +
                        " order by t." +
                        field +
                        (asc ? " asc" : " desc") +
                        ",t.id" +
                        (asc ? " asc" : " desc"),
                TaskEntity.class
        );
        params.forEach(q::setParameter);
        var rows = q.setMaxResults(first + 1).getResultList();
        boolean more = rows.size() > first;
        var page = rows.subList(0, Math.min(first, rows.size()));
        String cursor = null;
        if (!page.isEmpty()) {
            var last = page.getLast();
            String key = switch (sort) {
                case CREATED_ASC -> last.createdAt.toString();
                case UPDATED_DESC -> last.updatedAt.toString();
                case PRIORITY_DESC -> Integer.toString(last.priorityRank);
            };
            cursor = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            (pid + "|" + sort + "|" + key + "~" + last.id + "|").getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
        }
        return new TaskPage(
                page.stream().map(WorkService::view).toList(),
                cursor,
                more
        );
    }

    public Comment addComment(String email, CommentInput in) {
        var t = entity(email, in.taskId());
        spaces.lock(t.workspaceId);
        spaces.require(email, t.workspaceId);
        var c = new CommentEntity();
        c.taskId = t.id;
        c.body = in.body().trim();
        c.authorId = identity.me(email).id();
        c.createdAt = Instant.now();
        em.persist(c);
        events.record(t.workspaceId, t.id, "COMMENT_ADDED", c.authorId, t.version);
        return commentView(c);
    }

    private Comment commentView(CommentEntity c) {
        return new Comment(
                c.id,
                c.taskId,
                c.body,
                c.authorId,
                c.createdAt.toString()
        );
    }

    public List<Comment> comments(
            String email,
            String tid,
            int first,
            String after
    ) {
        entity(email, tid);
        pageSize(first);
        var q = em
                .createQuery(
                        "from CommentEntity where taskId=:t" +
                                (after == null ? "" : " and id<:after") +
                                " order by id desc",
                        CommentEntity.class
                )
                .setParameter("t", tid);
        if (after != null) q.setParameter("after", after);
        return q
                .setMaxResults(first)
                .getResultList()
                .stream()
                .map(this::commentView)
                .toList();
    }

    public List<Activity> activity(
            String email,
            String wid,
            int first,
            String after
    ) {
        spaces.require(email, wid);
        pageSize(first);
        var q = em
                .createQuery(
                        "from ActivityEntity where workspaceId=:w" +
                                (after == null ? "" : " and id<:after") +
                                " order by id desc",
                        ActivityEntity.class
                )
                .setParameter("w", wid);
        if (after != null) q.setParameter("after", after);
        return q
                .setMaxResults(first)
                .getResultList()
                .stream()
                .map(a ->
                        new Activity(
                                a.id,
                                a.type,
                                a.entityId,
                                a.actorId,
                                a.createdAt.toString()
                        )
                )
                .toList();
    }
}
