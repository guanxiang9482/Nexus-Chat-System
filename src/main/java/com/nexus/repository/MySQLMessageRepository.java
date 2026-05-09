package com.nexus.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.Message;
import com.nexus.exception.RepositoryException;

/**
 * MySQL implementation of MessageRepository.
 * The only class in the project that contains message-related SQL.
 */
public class MySQLMessageRepository implements MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MySQLMessageRepository.class);

    private final DataSource db;

    public MySQLMessageRepository(DataSource db) {
        this.db = db;
    }

    // ----------------------------------------------------------------
    // save()
    // ----------------------------------------------------------------
    @Override
    public void save(Message message) {
        String sql = """
            INSERT INTO messages (id, room_id, sender_id, content, type, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, message.getId());
            ps.setString(2, message.getRoomId());
            ps.setString(3, message.getSenderId());   // may be null — JDBC handles it
            ps.setString(4, message.getContent());
            ps.setString(5, message.getType().name().toLowerCase());
            ps.setTimestamp(6, Timestamp.from(message.getCreatedAt()));
            ps.executeUpdate();

            log.debug("Message saved to room {}", message.getRoomId());

        } catch (SQLException e) {
            throw new RepositoryException("Failed to save message", e);
        }
    }

    // ----------------------------------------------------------------
    // findRecentByRoomId()
    // ----------------------------------------------------------------
    @Override
    public List<Message> findRecentByRoomId(String roomId, int limit) {
        // Subquery trick: get last N rows ordered DESC, then re-sort ASC
        // so the result arrives in chronological order for display
        String sql = """
            SELECT * FROM (
                SELECT * FROM messages
                WHERE room_id = ?
                ORDER BY created_at DESC
                LIMIT ?
            ) sub
            ORDER BY created_at ASC
            """;

        List<Message> messages = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roomId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                messages.add(mapRow(rs));
            }
            return messages;

        } catch (SQLException e) {
            throw new RepositoryException("Failed to fetch messages for room: " + roomId, e);
        }
    }

    // ----------------------------------------------------------------
    // mapRow() — private helper
    // ----------------------------------------------------------------
    private Message mapRow(ResultSet rs) throws SQLException {
        return Message.fromDatabase(
            rs.getString("id"),
            rs.getString("room_id"),
            rs.getString("sender_id"),   // may be null for SYSTEM messages
            rs.getString("content"),
            Message.Type.valueOf(rs.getString("type").toUpperCase()),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}