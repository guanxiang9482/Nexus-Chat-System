package com.nexus.exception;

/**
 * Thrown by AuthService when authentication or validation fails.
 *
 * Extends RuntimeException — callers are not forced to declare it,
 * keeping service method signatures clean. The LoginCommandHandler
 * catches it explicitly at the boundary and translates it to a
 * wire-protocol message (AUTH_FAIL ...).
 *
 * Important: message strings are safe to send to the client.
 * Never put internal system details (stack traces, SQL state) in this
 * exception's message — it travels over the wire as-is.
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}