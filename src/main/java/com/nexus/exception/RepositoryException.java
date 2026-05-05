package com.nexus.exception;

/**
 * Thrown by repository implementations when a database operation fails
 * for infrastructure reasons — connection lost, query timeout, constraint
 * violation that isn't a duplicate key, etc.
 *
 * Extends RuntimeException, NOT AuthException. This distinction is
 * load-bearing: a RepositoryException means "the database is broken",
 * not "the user's credentials are wrong". The LoginCommandHandler catches
 * it separately and responds with "Internal server error" rather than
 * exposing the failure reason to the client.
 *
 * The original SQLException is always preserved as the cause so that
 * server-side logs capture the full SQL error code and state for diagnosis.
 * It is never forwarded to the client.
 */
public final class RepositoryException extends RuntimeException {

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}