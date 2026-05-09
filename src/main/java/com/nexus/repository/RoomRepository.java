package com.nexus.repository;

import java.util.List;
import java.util.Optional;

import com.nexus.domain.ChatRoom;

/**
 * Contract for all room persistence operations.
 * No SQL here. No JDBC here. Just intent.
 * MySQLRoomRepository is the only class that knows about SQL.
 */
public interface RoomRepository {

    /**
     * Persist a brand new room to the database.
     */
    void save(ChatRoom room);

    /**
     * Find a room by its unique ID.
     * Returns Optional.empty() if not found.
     */
    Optional<ChatRoom> findById(String id);

    /**
     * Find a room by its unique name.
     * Returns Optional.empty() if not found.
     */
    Optional<ChatRoom> findByName(String name);

    /**
     * Returns all active rooms.
     * Used for /list command.
     */
    List<ChatRoom> findAllActive();

    /**
     * Add a user to a room.
     * Inserts a row into room_members.
     */
    void addMember(String roomId, String userId, String role);

    /**
     * Remove a user from a room.
     * Deletes the row from room_members.
     */
    void removeMember(String roomId, String userId);

    /**
     * Check if a user is already a member of a room.
     */
    boolean isMember(String roomId, String userId);

    /**
     * Returns all room IDs a user is currently a member of.
     */
    List<String> findRoomIdsByUserId(String userId);
}