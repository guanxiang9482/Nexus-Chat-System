package com.nexus.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.ChatRoom;
import com.nexus.exception.RepositoryException;

/**
 * MySQL implementation of RoomRepository.
 * This is the ONLY class in the entire project that contains
 * room-related SQL. All other classes are shielded from JDBC.
 */
public class MySQLRoomRepository implements RoomRepository {

    private static final Logger log = LoggerFactory.getLogger(MySQLRoomRepository.class);

    private final DataSource db;

    public MySQLRoomRepository(DataSource db) {
        this.db = db;
    }

    // ----------------------------------------------------------------
    // save()
    // ----------------------------------------------------------------
    @Override
    public void save(ChatRoom room) {
        String sql = """
            INSERT INTO rooms (id, name, type, created_by, created_at, is_active)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, room.getId());
            ps.setString(2, room.getName());
            ps.setString(3, room.getType().name().toLowerCase());
            ps.setString(4, room.getCreatedBy());
            ps.setTimestamp(5, Timestamp.from(room.getCreatedAt()));
            ps.setBoolean(6, room.isActive());
            ps.executeUpdate();

            log.debug("Room saved: {}", room.getName());

        } catch (SQLIntegrityConstraintViolationException e) {
            // Unique constraint on name was violated
            throw new RepositoryException("Room name already exists: " + room.getName(), e);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save room: " + room.getName(), e);
        }
    }

    // ----------------------------------------------------------------
    // findById()
    // ----------------------------------------------------------------
    @Override
    public Optional<ChatRoom> findById(String id) {
        String sql = "SELECT * FROM rooms WHERE id = ? AND is_active = 1";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RepositoryException("Failed to find room by id: " + id, e);
        }
    }

    // ----------------------------------------------------------------
    // findByName()
    // ----------------------------------------------------------------
    @Override
    public Optional<ChatRoom> findByName(String name) {
        String sql = "SELECT * FROM rooms WHERE name = ? AND is_active = 1";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RepositoryException("Failed to find room by name: " + name, e);
        }
    }

    // ----------------------------------------------------------------
    // findAllActive()
    // ----------------------------------------------------------------
    @Override
    public List<ChatRoom> findAllActive() {
        String sql = "SELECT * FROM rooms WHERE is_active = 1 ORDER BY created_at DESC";
        List<ChatRoom> rooms = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                rooms.add(mapRow(rs));
            }
            return rooms;

        } catch (SQLException e) {
            throw new RepositoryException("Failed to fetch active rooms", e);
        }
    }

    // ----------------------------------------------------------------
    // addMember()
    // ----------------------------------------------------------------
    @Override
    public void addMember(String roomId, String userId, String role) {
        String sql = """
            INSERT INTO room_members (room_id, user_id, role, joined_at)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roomId);
            ps.setString(2, userId);
            ps.setString(3, role);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();

            log.debug("User {} added to room {} as {}", userId, roomId, role);

        } catch (SQLIntegrityConstraintViolationException e) {
            // User is already a member — not a fatal error, just log it
            log.warn("User {} is already a member of room {}", userId, roomId);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to add member to room", e);
        }
    }

    // ----------------------------------------------------------------
    // removeMember()
    // ----------------------------------------------------------------
    @Override
    public void removeMember(String roomId, String userId) {
        String sql = "DELETE FROM room_members WHERE room_id = ? AND user_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roomId);
            ps.setString(2, userId);
            ps.executeUpdate();

            log.debug("User {} removed from room {}", userId, roomId);

        } catch (SQLException e) {
            throw new RepositoryException("Failed to remove member from room", e);
        }
    }

    // ----------------------------------------------------------------
    // isMember()
    // ----------------------------------------------------------------
    @Override
    public boolean isMember(String roomId, String userId) {
        String sql = "SELECT 1 FROM room_members WHERE room_id = ? AND user_id = ?";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roomId);
            ps.setString(2, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            throw new RepositoryException("Failed to check membership", e);
        }
    }

    // ----------------------------------------------------------------
    // findRoomIdsByUserId()
    // ----------------------------------------------------------------
    @Override
    public List<String> findRoomIdsByUserId(String userId) {
        String sql = "SELECT room_id FROM room_members WHERE user_id = ?";
        List<String> roomIds = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                roomIds.add(rs.getString("room_id"));
            }
            return roomIds;

        } catch (SQLException e) {
            throw new RepositoryException("Failed to find rooms for user: " + userId, e);
        }
    }

    // ----------------------------------------------------------------
    // mapRow() — private helper
    // Converts one ResultSet row → ChatRoom domain object.
    // Lives here because only this class knows the column names.
    // ----------------------------------------------------------------
    private ChatRoom mapRow(ResultSet rs) throws SQLException {
        return ChatRoom.fromDatabase(
            rs.getString("id"),
            rs.getString("name"),
            ChatRoom.Type.valueOf(rs.getString("type").toUpperCase()),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("is_active")
        );
    }
}