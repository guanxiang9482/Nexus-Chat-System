package com.nexus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton configuration for the Nexus server.
 *
 * Pattern: Singleton via initialization-on-demand holder (Bill Pugh idiom).
 * Why: Thread-safe without synchronized blocks. The JVM class-loader guarantees
 * that the inner static class is initialized exactly once, lazily, and atomically.
 * This is the preferred idiom over double-checked locking in Java 21.
 *
 * Limitation acknowledged: makes testing harder. For integration tests,
 * use the package-private constructor directly — do not call getInstance().
 */
public final class ServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    // --- Network ---
    private final int port;
    private final int bossThreads;
    private final int workerThreads;

    // --- Pipeline ---
    private final int maxFrameLengthBytes;
    private final int idleTimeoutSeconds;

    // --- Inner holder: initialized once by the JVM class-loader ---
    private static final class Holder {
        private static final ServerConfig INSTANCE = new ServerConfig();
    }

    /**
     * Package-private constructor for direct instantiation in tests.
     * Production code always uses getInstance().
     */
    ServerConfig() {
        this.port                = readInt("NEXUS_PORT",         8080);
        this.bossThreads         = readInt("NEXUS_BOSS_THREADS", 1);
        this.workerThreads       = readInt("NEXUS_WORKER_THREADS",
                                      Runtime.getRuntime().availableProcessors() * 2);
        this.maxFrameLengthBytes = readInt("NEXUS_MAX_FRAME",    8192);
        this.idleTimeoutSeconds  = readInt("NEXUS_IDLE_TIMEOUT", 60);

        validate();
        log.info("ServerConfig initialized: port={}, workers={}, maxFrame={}b, idle={}s",
                  port, workerThreads, maxFrameLengthBytes, idleTimeoutSeconds);
    }

    public static ServerConfig getInstance() {
        return Holder.INSTANCE;
    }

    // --- Validation: fail fast at startup, never at runtime ---
    private void validate() {
        require(port > 0 && port <= 65535,
                "NEXUS_PORT must be 1–65535, got: " + port);
        require(bossThreads > 0,
                "NEXUS_BOSS_THREADS must be > 0");
        require(workerThreads > 0,
                "NEXUS_WORKER_THREADS must be > 0");
        require(maxFrameLengthBytes >= 64 && maxFrameLengthBytes <= 65536,
                "NEXUS_MAX_FRAME must be 64–65536 bytes");
        require(idleTimeoutSeconds >= 10,
                "NEXUS_IDLE_TIMEOUT must be >= 10 seconds");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("[ServerConfig] Validation failed: " + message);
        }
    }

    private static int readInt(String envKey, int defaultValue) {
        String raw = System.getenv(envKey);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid value for env var '{}': '{}'. Using default: {}",
                     envKey, raw, defaultValue);
            return defaultValue;
        }
    }

    // --- Immutable accessors only — no setters ---
    public int getPort()                { return port; }
    public int getBossThreads()         { return bossThreads; }
    public int getWorkerThreads()       { return workerThreads; }
    public int getMaxFrameLengthBytes() { return maxFrameLengthBytes; }
    public int getIdleTimeoutSeconds()  { return idleTimeoutSeconds; }
}