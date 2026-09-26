package com.teamflow.identity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    public String id = java.util.UUID.randomUUID().toString();

    public String email;
    public String name;
    public String password;
}
