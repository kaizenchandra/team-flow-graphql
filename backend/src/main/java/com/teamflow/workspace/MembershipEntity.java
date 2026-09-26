package com.teamflow.workspace;

import jakarta.persistence.*;

@Entity
@Table(name = "membership")
public class MembershipEntity {

    @Id
    public String id = java.util.UUID.randomUUID().toString();

    public String workspaceId;
    public String userId;

    @Enumerated(EnumType.STRING)
    public com.teamflow.platform.Api.Role role;
}
