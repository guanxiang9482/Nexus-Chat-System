package com.nexus.event;

import java.time.Instant;

import com.nexus.domain.ChatRoom;
import com.nexus.domain.User;

/**
 * Published when a user leaves or disconnects from a room.
 * ChatRoomService publishes this.
 * NotificationSubscriber listens and broadcasts to the room.
 */
public final class UserLeftRoomEvent {

    private final User     user;
    private final ChatRoom room;
    private final Instant  occurredAt;

    public UserLeftRoomEvent(User user, ChatRoom room) {
        this.user       = user;
        this.room       = room;
        this.occurredAt = Instant.now();
    }

    public User     getUser()       { return user; }
    public ChatRoom getRoom()       { return room; }
    public Instant  getOccurredAt() { return occurredAt; }

    @Override
    public String toString() {
        return "UserLeftRoomEvent{user='" + user.getUsername()
             + "', room='" + room.getName() + "'}";
    }
}