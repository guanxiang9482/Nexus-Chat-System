package com.nexus.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Singleton that owns the HikariCP connection pool.
 *
 * Pattern: Object Pool (via HikariCP) + Singleton (initialization-on-demand holder).
 * Why: JDBC connections are expensive to create. HikariCP pre-warms a pool and
 * lends connections in microseconds. The Singleton ensures exactly one pool
 * exists for the JVM lifetime — multiple pools waste connections and hide leaks.
 *
 * Tuning note: poolSize should be set to min(DB max_connections, expected_concurrent_queries).
 * For a chat system, most queries are short — a pool of 10 handles hundreds of rps.
 */
public final class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final class Holder {
        private static final HikariDataSource INSTANCE = buildPool();
    }

    public static DataSource getDataSource() {
        return Holder.INSTANCE;
    }

    private static HikariDataSource buildPool() {
        HikariConfig cfg = new HikariConfig();

        cfg.setJdbcUrl(env("NEXUS_DB_URL",
                "jdbc:mysql://localhost:3306/nexus_db?useSSL=false&serverTimezone=UTC"));
        cfg.setUsername(env("NEXUS_DB_USER", "nexus"));
        cfg.setPassword(env("NEXUS_DB_PASS", "nexus_secret"));

        // Pool sizing — start conservative, tune under load
        cfg.setMaximumPoolSize(readInt("NEXUS_DB_POOL_SIZE", 10));
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(3_000);   // fail fast: 3s to acquire a connection
        cfg.setIdleTimeout(600_000);        // return idle connections after 10 min
        cfg.setMaxLifetime(1_800_000);      // recycle connections every 30 min

        // Leak detection: log a warning if a connection is held > 5s
        // This catches cases where a repository forgot to close in a finally block
        cfg.setLeakDetectionThreshold(5_000);

        cfg.setPoolName("nexus-hikari-pool");

        // Validate connections before lending (catches stale connections)
        cfg.setConnectionTestQuery("SELECT 1");

        log.info("HikariCP pool initialized: maxSize={}, url={}",
                cfg.getMaximumPoolSize(), cfg.getJdbcUrl());
        return new HikariDataSource(cfg);
    }

    private static String env(String key, String defaultVal) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultVal;
    }

    private static int readInt(String key, int defaultVal) {
        try { return Integer.parseInt(System.getenv(key)); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private DatabaseConfig() {}
}