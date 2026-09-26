package com.teamflow.platform;

import com.teamflow.identity.IdentityService;
import com.teamflow.platform.Api.*;
import com.teamflow.work.WorkService;
import com.teamflow.workspace.WorkspaceService;
import jakarta.validation.Valid;

import java.security.Principal;
import java.util.*;

import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

@Controller
public class GraphqlController {

    private final IdentityService identity;
    private final WorkspaceService spaces;
    private final WorkService work;

    public GraphqlController(
            IdentityService identity,
            WorkspaceService spaces,
            WorkService work
    ) {
        this.identity = identity;
        this.spaces = spaces;
        this.work = work;
    }

    static String who(Principal p) {
        if (p == null) throw Problem.forbidden();
        return p.getName();
    }

    @QueryMapping
    public User me(Principal p) {
        return identity.me(who(p));
    }

    @QueryMapping
    public List<Workspace> workspaces(Principal p) {
        return spaces.list(who(p));
    }

    @QueryMapping
    public List<Member> members(Principal p, @Argument String workspaceId) {
        return spaces.members(who(p), workspaceId);
    }

    @QueryMapping
    public List<Project> projects(Principal p, @Argument String workspaceId) {
        return work.projects(who(p), workspaceId);
    }

    @BatchMapping(typeName = "Workspace", field = "projects")
    public Map<Workspace, List<Project>> nestedProjects(
            List<Workspace> values,
            Principal p
    ) {
        return work.projectsBatch(who(p), values);
    }

    @QueryMapping
    public Task task(Principal p, @Argument String id) {
        return work.task(who(p), id);
    }

    @QueryMapping
    public TaskPage tasks(
            Principal p,
            @Argument String projectId,
            @Argument TaskFilter filter,
            @Argument TaskSort sort,
            @Argument int first,
            @Argument String after
    ) {
        return work.tasks(who(p), projectId, filter, sort, first, after);
    }

    @QueryMapping
    public List<Comment> comments(
            Principal p,
            @Argument String taskId,
            @Argument int first,
            @Argument String after
    ) {
        return work.comments(who(p), taskId, first, after);
    }

    @QueryMapping
    public List<Activity> activity(
            Principal p,
            @Argument String workspaceId,
            @Argument int first,
            @Argument String after
    ) {
        return work.activity(who(p), workspaceId, first, after);
    }

    @BatchMapping(typeName = "Task", field = "assignee")
    public Map<Task, User> assignees(List<Task> tasks) {
        var users = identity.users(
                tasks.stream().map(Task::assigneeId).filter(Objects::nonNull).toList()
        );
        var map = new HashMap<Task, User>();
        tasks.forEach(t -> map.put(t, users.get(t.assigneeId())));
        return map;
    }

    @BatchMapping(typeName = "Comment", field = "author")
    public Map<Comment, User> authors(List<Comment> rows) {
        var users = identity.users(rows.stream().map(Comment::authorId).toList());
        var map = new HashMap<Comment, User>();
        rows.forEach(c -> map.put(c, users.get(c.authorId())));
        return map;
    }

    @BatchMapping(typeName = "Activity", field = "actor")
    public Map<Activity, User> actors(List<Activity> rows) {
        var users = identity.users(rows.stream().map(Activity::actorId).toList());
        var map = new HashMap<Activity, User>();
        rows.forEach(c -> map.put(c, users.get(c.actorId())));
        return map;
    }

    @MutationMapping
    public WorkspacePayload createWorkspace(
            Principal p,
            @Argument @Valid WorkspaceInput input
    ) {
        return new WorkspacePayload(spaces.create(who(p), input));
    }

    @MutationMapping
    public MemberPayload setMember(
            Principal p,
            @Argument @Valid MemberInput input
    ) {
        return new MemberPayload(spaces.setMember(who(p), input));
    }

    @MutationMapping
    public DeletePayload removeMember(
            Principal p,
            @Argument @Valid RemoveMemberInput input
    ) {
        return new DeletePayload(spaces.removeMember(who(p), input));
    }

    @MutationMapping
    public ProjectPayload createProject(
            Principal p,
            @Argument @Valid ProjectInput input
    ) {
        return new ProjectPayload(work.createProject(who(p), input));
    }

    @MutationMapping
    public TaskPayload createTask(Principal p, @Argument @Valid TaskInput input) {
        return new TaskPayload(work.createTask(who(p), input));
    }

    @MutationMapping
    public TaskPayload updateTask(
            Principal p,
            @Argument @Valid UpdateTaskInput input
    ) {
        return new TaskPayload(work.updateTask(who(p), input));
    }

    @MutationMapping
    public DeletePayload deleteTask(
            Principal p,
            @Argument @Valid DeleteTaskInput input
    ) {
        return new DeletePayload(work.deleteTask(who(p), input));
    }

    @MutationMapping
    public CommentPayload addComment(
            Principal p,
            @Argument @Valid CommentInput input
    ) {
        return new CommentPayload(work.addComment(who(p), input));
    }
}
