package com.nexus.repository;

import java.util.Optional;

import com.nexus.domain.User;

/**
 * Repository interface — the only thing AuthService depends on.
 *
 * Pattern: Repository (Evans DDD) + Dependency Inversion.
 * AuthService holds a UserRepository reference, not a MySQLUserRepository.
 * This means unit tests inject a FakeUserRepository (HashMap-backed),
 * integration tests use the real MySQL implementation.
 *
 * Optional<User> return types force callers to handle the absent case
 * explicitly — no NullPointerExceptions propagating into the service layer.
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findById(String id);

    /**
     * Persists a new user. Throws DuplicateUserException if username exists.
     * Never returns null — returns the saved entity with any DB-generated fields.
     */
    User save(User user);

    void updateLastLogin(String userId);
}