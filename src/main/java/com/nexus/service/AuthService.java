package com.nexus.service;

import java.util.Date;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.domain.User;
import com.nexus.exception.AuthException;
import com.nexus.repository.UserRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Façade over auth operations: registration, login, token verification.
 *
 * Pattern: Façade — callers (LoginCommandHandler) see only three clean methods.
 * Bcrypt, JWT signing, and repository access are all hidden behind this boundary.
 *
 * Threading: all three methods are safe to call from multiple threads.
 * BCrypt.hashpw() and BCrypt.checkpw() are thread-safe.
 * JWT signing with MACSigner is thread-safe after construction.
 * UserRepository implementations must be thread-safe (MySQLUserRepository is,
 * because each call acquires its own connection from the pool).
 *
 * Security decisions:
 * - bcrypt cost factor 12: ~250ms on modern hardware. Deliberately slow to
 *   make brute-force attacks expensive. Increase to 14 if hardware allows.
 * - JWT signed with HMAC-SHA256. Secret loaded from env — never hardcoded.
 * - Token TTL: 24 hours. Clients must re-authenticate after expiry.
 */
public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int    BCRYPT_COST  = 12;
    private static final long   TOKEN_TTL_MS = 24L * 60 * 60 * 1000; // 24 hours

    private final UserRepository userRepository;
    private final JWSSigner      jwsSigner;
    private final JWSVerifier    jwsVerifier;
    private final String         jwtSecret;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.jwtSecret      = loadJwtSecret();
        try {
            byte[] secretBytes = jwtSecret.getBytes();
            this.jwsSigner  = new MACSigner(secretBytes);
            this.jwsVerifier = new MACVerifier(secretBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JWT signer", e);
        }
    }

    /**
     * Registers a new user. Returns a JWT on success.
     * Throws DuplicateUserException if username is taken.
     * Throws AuthException if input validation fails.
     */
    public String register(String username, String password, String displayName) {
        validateCredentials(username, password);

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
        User user   = User.newUser(username, hash, displayName);
        userRepository.save(user); // throws DuplicateUserException on conflict

        log.info("User registered: {}", username);
        return generateToken(user);
    }

    /**
     * Authenticates a user. Returns a JWT on success.
     * Throws AuthException with a generic message on any failure.
     * (Generic message is intentional — never reveal whether the username exists.)
     */
    public String login(String username, String password) {
        validateCredentials(username, password);

        User user = userRepository
            .findByUsername(username)
            .orElseThrow(() -> new AuthException("Invalid username or password"));

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            log.warn("Failed login attempt for username: {}", username);
            throw new AuthException("Invalid username or password");
        }

        userRepository.updateLastLogin(user.getId());
        log.info("User logged in: {}", username);
        return generateToken(user);
    }

    /**
     * Verifies a JWT and returns the user ID encoded in it.
     * Throws AuthException if the token is invalid, expired, or tampered.
     */
    public String verifyToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(jwsVerifier)) {
                throw new AuthException("Invalid token signature");
            }

            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                throw new AuthException("Token has expired");
            }

            return jwt.getJWTClaimsSet().getSubject(); // returns userId

        } catch (AuthException e) {
            throw e; // re-throw domain exceptions as-is
        } catch (Exception e) {
            throw new AuthException("Malformed token");
        }
    }

    // --- Private helpers ---

    private String generateToken(User user) {
        try {
            // Standard JWT claims: sub (subject) is user ID, plus custom claims for username and display name.
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + TOKEN_TTL_MS))
                .build();

            SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(jwsSigner);
            return jwt.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private void validateCredentials(String username, String password) {
        if (username == null || username.isBlank() || username.length() > 32) {
            throw new AuthException("Username must be 1–32 characters");
        }
        if (password == null || password.length() < 8) {
            throw new AuthException("Password must be at least 8 characters");
        }
        // Only allow alphanumeric and underscores — prevents injection at the app level
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new AuthException("Username may only contain letters, digits, and underscores");
        }
    }

    private static String loadJwtSecret() {
        String secret = System.getenv("NEXUS_JWT_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "NEXUS_JWT_SECRET env var must be set to a string of at least 32 characters");
        }
        return secret;
    }
}