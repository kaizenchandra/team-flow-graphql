package com.teamflow.work;

import jakarta.persistence.*;

@Entity
@Table(name = "project")
public class ProjectEntity {

    @Id
    public String id = java.util.UUID.randomUUID().toString();

    public String workspaceId;
    public String name;
}
