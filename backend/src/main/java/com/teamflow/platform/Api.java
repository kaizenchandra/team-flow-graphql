package com.teamflow.platform;

import jakarta.validation.constraints.*;

import java.util.List;

public final class Api {

    private Api() {
    }

    public enum Role {
        OWNER,
        ADMIN,
        MEMBER,
    }

    public enum TaskStatus {
        TODO,
        IN_PROGRESS,
        DONE,
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT,
    }

    public enum TaskSort {
        CREATED_ASC,
        UPDATED_DESC,
        PRIORITY_DESC,
    }

    public record User(String id, String email, String name) {
    }

    public record Workspace(String id, String name, Role role) {
    }

    public record Member(User user, Role role) {
    }

    public record Project(String id, String workspaceId, String name) {
    }

    public record Task(
            String id,
            String projectId,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            String assigneeId,
            String dueDate,
            long version,
            String createdAt,
            String updatedAt
    ) {
    }

    public record TaskPage(
            List<Task> nodes,
            String endCursor,
            boolean hasNextPage
    ) {
    }

    public record Comment(
            String id,
            String taskId,
            String body,
            String authorId,
            String createdAt
    ) {
    }

    public record Activity(
            String id,
            String type,
            String entityId,
            String actorId,
            String createdAt
    ) {
    }

    public record ChangeEvent(
            String id,
            String workspaceId,
            String entityId,
            String type,
            String timestamp,
            Long version
    ) {
    }

    public record WorkspaceInput(@NotBlank @Size(max = 120) String name) {
    }

    public record MemberInput(
            @NotBlank String workspaceId,
            @Email @NotBlank String email,
            @NotNull Role role
    ) {
    }

    public record RemoveMemberInput(
            @NotBlank String workspaceId,
            @NotBlank String userId
    ) {
    }

    public record ProjectInput(
            @NotBlank String workspaceId,
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record TaskInput(
            @NotBlank String projectId,
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 10000) String description,
            @NotNull TaskStatus status,
            @NotNull Priority priority,
            String assigneeId,
            String dueDate
    ) {
    }

    public record UpdateTaskInput(
            @NotBlank String id,
            @Min(0) long version,
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 10000) String description,
            @NotNull TaskStatus status,
            @NotNull Priority priority,
            String assigneeId,
            String dueDate
    ) {
    }

    public record DeleteTaskInput(@NotBlank String id, @Min(0) long version) {
    }

    public record CommentInput(
            @NotBlank String taskId,
            @NotBlank @Size(max = 4000) String body
    ) {
    }

    public record TaskFilter(
            TaskStatus status,
            Priority priority,
            String assigneeId
    ) {
    }

    public record WorkspacePayload(Workspace workspace) {
    }

    public record MemberPayload(Member member) {
    }

    public record ProjectPayload(Project project) {
    }

    public record TaskPayload(Task task) {
    }

    public record CommentPayload(Comment comment) {
    }

    public record DeletePayload(String id) {
    }
}
