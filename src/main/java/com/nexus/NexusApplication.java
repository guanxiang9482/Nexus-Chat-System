package com.nexus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.config.ServerConfig;
import com.nexus.server.NettyServerBootstrap;

/**
 * Application entry point. Responsibilities:
 *  1. Load configuration (Singleton, validated at startup).
 *  2. Register a JVM shutdown hook for graceful termination on SIGTERM/SIGINT.
 *  3. Start the Netty bootstrap and block until the server channel closes.
 */
public final class NexusApplication {

    private static final Logger log = LoggerFactory.getLogger(NexusApplication.class);

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.getInstance();

        NettyServerBootstrap bootstrap = new NettyServerBootstrap(config);

        // Graceful shutdown on Ctrl-C / SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received.");
            bootstrap.close();
        }, "nexus-shutdown-hook"));

        try {
            bootstrap.start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Server interrupted.", e);
        }
    }
}