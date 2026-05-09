package com.nexus.domain;

import java.time.Instant;

/**
 * Immutable value object representing a single chat message.
 * sender is null for system-generated messages (type = SYSTEM).
 * Safe to share across all Netty worker threads.
 */
public final class Message {

    public enum Type {
        TEXT,    // normal user message
        SYSTEM,  // server notification e.g. "Alice joined"
        BLOCKED  // AI blocked this message (Milestone 4)
    }

    private final String  id;
    private final String  roomId;
    private final String  senderId;   // null for SYSTEM messages
    private final String  content;
    private final Type    type;
    private final Instant createdAt;

    // Private constructor
    private Message(String id, String roomId, String senderId,
                    String content, Type type, Instant createdAt) {
        this.id        = id;
        this.roomId    = roomId;
        this.senderId  = senderId;
        this.content   = content;
        this.type      = type;
        this.createdAt = createdAt;
    }

    /**
     * Factory method for a new user message (before persisting).
     */
    public static Message newTextMessage(String roomId,
                                         String senderId,
                                         String content) {
        return new Message(
            java.util.UUID.randomUUID().toString(),
            roomId,
            senderId,
            content,
            Type.TEXT,
            Instant.now()
        );
    }

    /**
     * Factory method for a system-generated message.
     * senderId is null — no user sent this.
     */
    public static Message newSystemMessage(String roomId, String content) {
        return new Message(
            java.util.UUID.randomUUID().toString(),
            roomId,
            null,           // no sender
            content,
            Type.SYSTEM,
            Instant.now()
        );
    }

    /**
     * Factory method for reconstructing a message from the database.
     */
    public static Message fromDatabase(String id, String roomId,
                                       String senderId, String content,
                                       Type type, Instant createdAt) {
        return new Message(id, roomId, senderId, content, type, createdAt);
    }

    // ----------------------------------------------------------------
    // Getters — no setters, ever
    // ----------------------------------------------------------------
    public String  getId()        { return id; }
    public String  getRoomId()    { return roomId; }
    public String  getSenderId()  { return senderId; }  // may be null
    public String  getContent()   { return content; }
    public Type    getType()      { return type; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isSystemMessage() { return type == Type.SYSTEM; }

    @Override
    public String toString() {
        return "Message{id='" + id + "', roomId='" + roomId +
               "', type=" + type + ", content='" + content + "'}";
    }
}