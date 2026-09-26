package com.teamflow.work;

import jakarta.persistence.*;

@Entity
@Table(name = "activity")
public class ActivityEntity {

    @Id
    public String id = com.teamflow.platform.TimeId.next();

    public String workspaceId;
    public String actorId;
    public String type;
    public String entityId;
    public java.time.Instant createdAt;
}
