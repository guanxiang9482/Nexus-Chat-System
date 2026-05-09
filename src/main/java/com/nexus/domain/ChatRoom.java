package com.nexus.domain;

import java.time.Instant;

/**
 * Immutable value object representing a chat room.
 * State change = fetch a new instance from the repository.
 * Safe to share across all Netty worker threads.
 */
public final class ChatRoom {

    public enum Type {
        PUBLIC, PRIVATE
    }

    private final String  id;
    private final String  name;
    private final Type    type;
    private final String  createdBy;   // user id of the owner
    private final Instant createdAt;
    private final boolean isActive;

    // Private constructor — only factory method can create instances
    private ChatRoom(String id, String name, Type type,
                     String createdBy, Instant createdAt, boolean isActive) {
        this.id        = id;
        this.name      = name;
        this.type      = type;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.isActive  = isActive;
    }

    /**
     * Factory method for creating a brand new room (before it is persisted).
     * Generates a fresh UUID. Sets createdAt to now.
     * UUID : Universally Unique Identifier, a 128-bit number used to uniquely identify objects or entities in distributed systems.
     * If we do not have factory method, the caller has to know all the details of how to create a ChatRoom. 
     * If the caller forget a single one, it can lead to errors and inconsistencies.
     * By using a factory method, we can centralize the logic for creating ChatRoom instances, ensuring that they are always created correctly and consistently.
     * Before: ChatRoom room = new ChatRoom(UUID.randomUUID().toString(), name, type, createdBy, Instant.now(), true);
     * After: ChatRoom room = ChatRoom.newRoom(name, type, createdBy);
     */
    public static ChatRoom newRoom(String name, Type type, String createdBy) {
        return new ChatRoom(
            java.util.UUID.randomUUID().toString(),
            name,
            type,
            createdBy,
            Instant.now(),
            true
        );
    }

    /**
     * Factory method for reconstructing a room from the database.
     * All fields are supplied — nothing is generated.
     */
    public static ChatRoom fromDatabase(String id, String name, Type type,
                                        String createdBy, Instant createdAt,
                                        boolean isActive) {
        return new ChatRoom(id, name, type, createdBy, createdAt, isActive);
    }

    // ----------------------------------------------------------------
    // Getters — no setters, ever
    // ----------------------------------------------------------------
    public String  getId()        { return id; }
    public String  getName()      { return name; }
    public Type    getType()      { return type; }
    public String  getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isActive()     { return isActive; }

    @Override
    public String toString() {
        return "ChatRoom{id='" + id + "', name='" + name + "', type=" + type + "}";
    }
}