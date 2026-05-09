package com.nexus.repository;

import java.util.List;

import com.nexus.domain.Message;

/**
 * Contract for all message persistence operations.
 */
public interface MessageRepository {

    /**
     * Persist a new message to the database.
     */
    void save(Message message);

    /**
     * Retrieve the most recent N messages from a room.
     * Used when a user joins — show them recent history.
     */
    List<Message> findRecentByRoomId(String roomId, int limit);
}