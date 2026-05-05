package com.nexus.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.User;
import com.nexus.exception.DuplicateUserException;
import com.nexus.exception.RepositoryException;

/**
 * MySQL implementation of UserRepository.
 *
 * Rules enforced here:
 * 1. Always use PreparedStatement — never string-concat SQL (SQL injection).
 * 2. Always close connections in try-with-resources — never rely on GC.
 * 3. Translate SQLException into domain exceptions — SQL errors never
 *    propagate beyond the repository boundary.
 * 4. Log at DEBUG for query execution, ERROR for failures — never log
 *    passwords or sensitive field values.
 */
public final class MySQLUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(MySQLUserRepository.class);
    private final DataSource dataSource;

    public MySQLUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        final String sql =
            "SELECT id, username, password_hash, display_name, created_at, is_active " +
            "FROM users WHERE username = ? AND is_active = 1 LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            log.error("findByUsername failed for '{}': {}", username, e.getMessage(), e);
            throw new RepositoryException("Failed to find user by username", e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        final String sql =
            "SELECT id, username, password_hash, display_name, created_at, is_active " +
            "FROM users WHERE id = ? LIMIT 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            log.error("findById failed: {}", e.getMessage(), e);
            throw new RepositoryException("Failed to find user by id", e);
        }
    }

    @Override
    public User save(User user) {
        final String sql =
            "INSERT INTO users (id, username, password_hash, display_name, created_at, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getDisplayName());
            ps.setTimestamp(5, Timestamp.from(user.getCreatedAt()));
            ps.setBoolean(6, user.isActive());
            ps.executeUpdate();

            log.debug("User saved: id={}, username={}", user.getId(), user.getUsername());
            return user;

        } catch (SQLIntegrityConstraintViolationException e) {
            // MySQL error 1062 = duplicate key — translate to a clean domain exception
            throw new DuplicateUserException(
                "Username already taken: " + user.getUsername(), e);
        } catch (SQLException e) {
            log.error("save failed for user '{}': {}", user.getUsername(), e.getMessage(), e);
            throw new RepositoryException("Failed to save user", e);
        }
    }

    @Override
    public void updateLastLogin(String userId) {
        final String sql = "UPDATE users SET last_login_at = NOW() WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-fatal — log and continue. Login already succeeded.
            log.warn("updateLastLogin failed for id={}: {}", userId, e.getMessage());
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("is_active")
        );
    }
}