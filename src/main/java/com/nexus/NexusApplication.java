package com.nexus;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexus.config.DatabaseConfig;
import com.nexus.config.ServerConfig;
import com.nexus.handler.LoginCommandHandler;
import com.nexus.repository.MySQLUserRepository;
import com.nexus.repository.UserRepository;
import com.nexus.server.NettyServerBootstrap;
import com.nexus.service.AuthService;

public final class NexusApplication {

    private static final Logger log = LoggerFactory.getLogger(NexusApplication.class);

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.getInstance();

        // Data layer
        UserRepository   userRepo    = new MySQLUserRepository(DatabaseConfig.getDataSource());

        // Service layer
        AuthService      authService = new AuthService(userRepo);

        // Dedicated executor for bcrypt — keeps Netty workers unblocked
        // Virtual threads (Java 21) are ideal here: cheap, plentiful, block-friendly
        ExecutorService  authExec    = Executors.newVirtualThreadPerTaskExecutor();

        // Handler wiring
        LoginCommandHandler loginHandler = new LoginCommandHandler(authService, authExec);

        NettyServerBootstrap bootstrap =
            new NettyServerBootstrap(config, loginHandler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received.");
            authExec.shutdown();
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