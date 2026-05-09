package com.nexus.event;

import java.time.Instant;

import com.nexus.domain.ChatRoom;
import com.nexus.domain.User;

/**
 * Published when a user successfully joins a room.
 * ChatRoomService publishes this.
 * NotificationSubscriber listens and broadcasts to the room.
 */
public final class UserJoinedRoomEvent {

    private final User     user;
    private final ChatRoom room;
    private final Instant  occurredAt;

    public UserJoinedRoomEvent(User user, ChatRoom room) {
        this.user       = user;
        this.room       = room;
        this.occurredAt = Instant.now();
    }

    public User     getUser()       { return user; }
    public ChatRoom getRoom()       { return room; }
    public Instant  getOccurredAt() { return occurredAt; }

    @Override
    public String toString() {
        return "UserJoinedRoomEvent{user='" + user.getUsername()
             + "', room='" + room.getName() + "'}";
    }
}