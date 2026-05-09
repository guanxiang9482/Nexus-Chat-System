package com.nexus;

import com.google.common.eventbus.EventBus;
import com.nexus.config.DatabaseConfig;
import com.nexus.config.ServerConfig;
import com.nexus.handler.AuthMiddlewareHandler;
import com.nexus.handler.ChatDispatchHandler;
import com.nexus.handler.LoginCommandHandler;
import com.nexus.repository.*;
import com.nexus.server.NettyServerBootstrap;
import com.nexus.service.AuthService;
import com.nexus.service.ChatRoomService;
import com.nexus.service.MessageService;
import com.nexus.subscriber.NotificationSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point. Wires every layer together.
 *
 * Dependency graph (read bottom up):
 *
 *   DataSource
 *       └── Repositories
 *               └── Services
 *                       └── EventBus → Subscribers
 *                       └── Handlers
 *                               └── NettyServerBootstrap
 */
public class NexusApplication {

    private static final Logger log = LoggerFactory.getLogger(NexusApplication.class);

    public static void main(String[] args) throws InterruptedException {

        log.info("Starting Nexus Chat System...");

        // ── Layer 1: Config ───────────────────────────────────────────
        ServerConfig serverConfig = ServerConfig.getInstance();

        // ── Layer 2: DataSource ───────────────────────────────────────
        DataSource dataSource = DatabaseConfig.getDataSource();

        // ── Layer 3: Repositories ─────────────────────────────────────
        UserRepository    userRepository    = new MySQLUserRepository(dataSource);
        RoomRepository    roomRepository    = new MySQLRoomRepository(dataSource);
        MessageRepository messageRepository = new MySQLMessageRepository(dataSource);

        // ── Layer 4: EventBus ─────────────────────────────────────────
        EventBus eventBus = new EventBus("nexus-event-bus");

        // ── Layer 5: Services ─────────────────────────────────────────
        AuthService    authService    = new AuthService(userRepository);
        ChatRoomService chatRoomService = new ChatRoomService(
            roomRepository, messageRepository, eventBus
        );
        MessageService messageService = new MessageService(
            messageRepository, eventBus
        );

        // ── Layer 6: Subscribers ──────────────────────────────────────
        NotificationSubscriber notificationSubscriber =
            new NotificationSubscriber(chatRoomService);

        // Register subscribers with the EventBus
        // From this point on, eventBus.post() reaches these subscribers
        eventBus.register(notificationSubscriber);

        // ── Layer 7: Executor for blocking auth work ──────────────────
        ExecutorService authExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // ── Layer 8: Handlers ─────────────────────────────────────────
        LoginCommandHandler loginCommandHandler = new LoginCommandHandler(
            authService, authExecutor
        );
        AuthMiddlewareHandler authMiddlewareHandler = new AuthMiddlewareHandler(
            authService, userRepository
        );
        ChatDispatchHandler chatDispatchHandler = new ChatDispatchHandler(
            chatRoomService, messageService
        );

        // ── Layer 9: Server ───────────────────────────────────────────
        NettyServerBootstrap server = new NettyServerBootstrap(
            serverConfig,
            loginCommandHandler,
            authMiddlewareHandler,
            chatDispatchHandler
        );

        // ── Shutdown hook ─────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received...");
            server.close();
            authExecutor.shutdown();
            log.info("Goodbye.");
        }));

        // ── Start ─────────────────────────────────────────────────────
        server.start();
    }
}