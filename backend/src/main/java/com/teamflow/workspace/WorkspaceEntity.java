package com.teamflow.workspace;

import jakarta.persistence.*;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity {

    @Id
    public String id = java.util.UUID.randomUUID().toString();

    public String name;
}
