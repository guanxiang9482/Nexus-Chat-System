package com.nexus.event;

import java.time.Instant;

import com.nexus.domain.Message;
import com.nexus.domain.User;

/**
 * Published when a user sends a message that has been persisted.
 * MessageService publishes this AFTER saving to the database.
 * NotificationSubscriber listens and broadcasts to the room.
 * AIModerationSubscriber will also listen in Milestone 4.
 */
public final class MessageCreatedEvent {

    private final Message message;
    private final User    sender;
    private final Instant occurredAt;

    public MessageCreatedEvent(Message message, User sender) {
        this.message    = message;
        this.sender     = sender;
        this.occurredAt = Instant.now();
    }

    public Message getMessage()    { return message; }
    public User    getSender()     { return sender; }
    public Instant getOccurredAt() { return occurredAt; }

    @Override
    public String toString() {
        return "MessageCreatedEvent{sender='" + sender.getUsername()
             + "', room='" + message.getRoomId() + "'}";
    }
}