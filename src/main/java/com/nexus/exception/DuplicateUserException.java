package com.nexus.exception;

/**
 * Thrown by MySQLUserRepository.save() when a username already exists.
 *
 * Extends AuthException so the LoginCommandHandler's existing catch
 * block for AuthException acts as a safety net — but LoginCommandHandler
 * catches DuplicateUserException first (more specific type) to send a
 * more precise "Username already taken" message rather than a generic
 * auth failure.
 *
 * This specificity matters: the client deserves to know whether the
 * failure was bad credentials vs. a name conflict, so they can prompt
 * the user to choose a different name rather than retry the password.
 */
public final class DuplicateUserException extends AuthException {

    public DuplicateUserException(String message, Throwable cause) {
        super(message, cause);
    }
}