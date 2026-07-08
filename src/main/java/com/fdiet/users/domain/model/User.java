package com.fdiet.users.domain.model;

import java.time.Instant;
import java.util.UUID;

public class User {
    private final UUID id;
    private Password password;
    private final String name;
    private final Email email;
    private final Role role;
    private final Instant createdAt;

    public User(UUID id, Password password, String name, Email email, Role role, Instant createdAt) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public User generate(Password password, String name, Email email, Role role, Instant createdAt) {
        return new User(UUID.randomUUID(), password, name, email, role, createdAt);
    }

    public User rehydrate(UUID id, Password password, String name, Email email, Role role, Instant createdAt) {
        return new User(id, password, name, email, role, createdAt);
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public UUID id() {
        return id;
    }

    public Password password() {
        return password;
    }

    public String name() {
        return name;
    }

    public Email email() {
        return email;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

}
