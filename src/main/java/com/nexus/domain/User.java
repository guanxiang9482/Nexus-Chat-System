package com.nexus.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain object. No JPA annotations, no framework coupling.
 * The repository maps ResultSet → User. Services receive User objects.
 * The network layer never touches User directly — it only sees JWT strings.
 *
 * Why immutable: concurrent access from multiple Netty threads is safe
 * without synchronization. A User's state never changes after construction —
 * if display_name is updated, a new User is fetched from the repository.
 */
public final class User {

    private final String   id;
    private final String   username;
    private final String   passwordHash;
    private final String   displayName;
    private final Instant  createdAt;
    private final boolean  active;

    public User(String id, String username, String passwordHash,
                String displayName, Instant createdAt, boolean active) {
        this.id           = id;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.displayName  = displayName;
        this.createdAt    = createdAt;
        this.active       = active;
    }

    /** Factory for new registrations — generates UUID and timestamps here. */
    public static User newUser(String username, String passwordHash, String displayName) {
        return new User(
            UUID.randomUUID().toString(),
            username,
            passwordHash,
            displayName,
            Instant.now(),
            true
        );
    }

    public String  getId()           { return id; }
    public String  getUsername()     { return username; }
    public String  getPasswordHash() { return passwordHash; }
    public String  getDisplayName()  { return displayName; }
    public Instant getCreatedAt()    { return createdAt; }
    public boolean isActive()        { return active; }
}