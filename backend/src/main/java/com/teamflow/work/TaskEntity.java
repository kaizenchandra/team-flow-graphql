package com.teamflow.work;

import jakarta.persistence.*;

@Entity
@Table(name = "task")
public class TaskEntity {

    @Id
    public String id = java.util.UUID.randomUUID().toString();

    public String projectId;
    public String workspaceId;
    public String title;
    public String description;

    @Enumerated(EnumType.STRING)
    public com.teamflow.platform.Api.TaskStatus status;

    @Enumerated(EnumType.STRING)
    public com.teamflow.platform.Api.Priority priority;

    public int priorityRank;
    public String assigneeId;
    public java.time.LocalDate dueDate;

    @Version
    public long version;

    public java.time.Instant createdAt;
    public java.time.Instant updatedAt;
}
