package com.teamflow.work;

import jakarta.persistence.*;

@Entity
@Table(name = "comment")
public class CommentEntity {

    @Id
    public String id = com.teamflow.platform.TimeId.next();

    public String taskId;
    public String authorId;
    public String body;
    public java.time.Instant createdAt;
}
